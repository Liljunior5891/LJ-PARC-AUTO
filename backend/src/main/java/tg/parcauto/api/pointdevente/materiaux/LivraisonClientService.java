package tg.parcauto.api.pointdevente.materiaux;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.caisse.SessionTravailService;
import tg.parcauto.api.pointdevente.stock.StockService;
import tg.parcauto.api.pointdevente.vente.LigneVente;
import tg.parcauto.api.pointdevente.vente.Vente;
import tg.parcauto.api.pointdevente.vente.VenteRepository;
import tg.parcauto.api.pointdevente.vente.VenteService;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * Livraison en plusieurs fois : le client emporte une partie de sa commande et
 * revient chercher le reste.
 * <p>
 * Le stock sort a la remise, ce qui garde le stock affiche egal a ce qui est en
 * magasin. Tant qu'elle n'est pas remise, la marchandise vendue reste reservee :
 * elle ne peut pas etre revendue a un autre client. Le paiement n'entre pas en
 * jeu ici : la vente et ce que le client doit restent ce qu'ils sont.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LivraisonClientService {

    private final BonLivraisonRepository repository;
    private final VenteRepository venteRepository;
    private final StockService stockService;
    private final NumerotationService numerotation;
    private final ReglageMateriauxService reglages;
    private final SoldeResteRepository soldeRepository;
    private final SessionTravailService sessionsTravail;

    /** Ventes dont il reste de la marchandise a remettre, les plus anciennes d'abord. */
    @Transactional(readOnly = true)
    public Page<MateriauxDto.VenteALivrer> resteALivrer(String q, Long clientId, Pageable pageable) {
        reglages.exiger();
        String motif = q == null ? "" : q.trim().toLowerCase();
        return venteRepository.aLivrer(motif, clientId, pageable).map(MateriauxDto.VenteALivrer::de);
    }

    /** Commandes qui ont un bon ou un solde, meme entierement remises : leurs documents restent accessibles. */
    @Transactional(readOnly = true)
    public Page<MateriauxDto.VenteALivrer> historique(String q, Long clientId, Pageable pageable) {
        reglages.exiger();
        String motif = q == null ? "" : q.trim().toLowerCase();
        return venteRepository.avecDocuments(motif, clientId, pageable).map(MateriauxDto.VenteALivrer::de);
    }

    /** Enregistre une remise : le stock sort, la reservation diminue, un bon est emis. */
    public MateriauxDto.BonReponse remettre(Long venteId, MateriauxDto.RemiseRequete demande, UtilisateurPrincipal auteur) {
        reglages.exiger();
        Vente vente = venteRepository.detail(venteId).orElseThrow(() -> ResourceNotFoundException.of("Vente", venteId));
        if (vente.getStatut() != Vente.Statut.VALIDEE) {
            throw new BusinessException("La commande " + vente.getNumero() + " est annulee");
        }
        BonLivraison bon = nouveauBon(vente, auteur.getNomComplet());
        bon.setRecuPar(vide(demande.recuPar()));
        bon.setNote(vide(demande.note()));
        for (MateriauxDto.LigneRemise saisie : demande.lignes()) {
            LigneVente ligne = vente.getLignes().stream()
                    .filter(l -> l.getId().equals(saisie.ligneId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Cette ligne n'appartient pas a la commande " + vente.getNumero()));
            if (saisie.quantite().compareTo(ligne.resteALivrer()) > 0) {
                throw new BusinessException("Il ne reste que " + ligne.resteALivrer().stripTrailingZeros().toPlainString()
                        + " a livrer pour " + ligne.getLibelle());
            }
            BigDecimal base = ligne.remettre(saisie.quantite());
            stockService.livrer(ligne.getArticle(), base, "Livraison " + bon.getNumero() + " - commande " + vente.getNumero(),
                    vente.getId());
            bon.ajouter(ligneBon(ligne, saisie.quantite(), base));
        }
        return MateriauxDto.BonReponse.de(repository.save(bon));
    }

    /** Remise saisie par erreur : la marchandise revient en stock et redevient a livrer. */
    public MateriauxDto.BonReponse annuler(Long bonId, String motif, UtilisateurPrincipal auteur) {
        reglages.exiger();
        BonLivraison bon = charger(bonId);
        if (bon.getStatut() == BonLivraison.Statut.ANNULE) {
            throw new BusinessException("Le bon " + bon.getNumero() + " est deja annule");
        }
        for (LigneBonLivraison l : bon.getLignes()) {
            LigneVente ligne = l.getLigneVente();
            ligne.reprendre(l.getQuantite(), l.getQuantiteBase());
            stockService.annulerLivraison(ligne.getArticle(), l.getQuantiteBase(),
                    "Annulation du bon " + bon.getNumero(), bon.getVente().getId());
        }
        bon.setStatut(BonLivraison.Statut.ANNULE);
        bon.setAnnuleLe(Instant.now());
        bon.setAnnulePar(auteur.getNomComplet());
        bon.setMotifAnnulation(vide(motif));
        return MateriauxDto.BonReponse.de(bon);
    }

    @Transactional(readOnly = true)
    public List<MateriauxDto.BonReponse> bonsDeLaVente(Long venteId) {
        reglages.exiger();
        return repository.findByVenteIdOrderByDateBonDescIdDesc(venteId).stream().map(MateriauxDto.BonReponse::de).toList();
    }

    @Transactional(readOnly = true)
    public BonLivraison charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Bon de livraison", id));
    }

    // ---------------------------------------------------------- solde du reste

    /**
     * Le client renonce a ce qu'il n'a pas emporte. La vente est corrigee et la
     * marchandise redevient disponible ; la valeur diminue d'abord ce qu'il doit,
     * et seul le surplus lui est rendu, sur une session de caisse ouverte.
     */
    public MateriauxDto.SoldeReponse solder(Long venteId, MateriauxDto.SoldeRequete demande, UtilisateurPrincipal auteur) {
        reglages.exiger();
        Vente vente = venteRepository.detail(venteId).orElseThrow(() -> ResourceNotFoundException.of("Vente", venteId));
        if (vente.getStatut() != Vente.Statut.VALIDEE) {
            throw new BusinessException("La commande " + vente.getNumero() + " est annulee");
        }
        SoldeReste solde = new SoldeReste();
        solde.setVente(vente);
        solde.setDateSolde(Instant.now());
        solde.setAuteurNom(auteur.getNomComplet());
        solde.setMotif(demande.motif().trim());

        BigDecimal brut = BigDecimal.ZERO;
        for (MateriauxDto.LigneRemise saisie : demande.lignes()) {
            LigneVente ligne = vente.getLignes().stream()
                    .filter(l -> l.getId().equals(saisie.ligneId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Cette ligne n'appartient pas a la commande " + vente.getNumero()));
            BigDecimal renonce = saisie.quantite();
            if (renonce.compareTo(ligne.resteALivrer()) > 0) {
                throw new BusinessException("Il ne reste que " + ligne.resteALivrer().stripTrailingZeros().toPlainString()
                        + " a livrer pour " + ligne.getLibelle());
            }
            // Toute la ligne : ses montants exacts ; sinon au prorata, sa remise suivant la quantite.
            boolean toute = renonce.compareTo(ligne.getQuantite()) == 0;
            BigDecimal remise = toute ? ligne.getRemise() : ligne.getRemise().multiply(renonce)
                    .divide(ligne.getQuantite(), 2, RoundingMode.HALF_UP);
            BigDecimal total = toute ? ligne.getTotal()
                    : ligne.getPrixUnitaire().multiply(renonce).setScale(2, RoundingMode.HALF_UP).subtract(remise);
            BigDecimal base = ligne.solder(renonce);
            ligne.setRemise(ligne.getRemise().subtract(remise));
            ligne.setTotal(ligne.getTotal().subtract(total));
            stockService.liberer(ligne.getArticle(), base);
            brut = brut.add(total);

            LigneSoldeReste l = new LigneSoldeReste();
            l.setLigneVente(ligne);
            l.setQuantite(renonce);
            l.setQuantiteBase(base);
            l.setRemise(remise);
            l.setTotal(total);
            solde.ajouter(l);
        }

        // La remise globale de la vente s'applique aussi a ce qui est retire.
        BigDecimal net = brut.compareTo(vente.getTotalBrut()) == 0 ? vente.getTotal()
                : brut.multiply(vente.getTotal()).divide(vente.getTotalBrut(), 2, RoundingMode.HALF_UP);
        vente.setTotalBrut(vente.getTotalBrut().subtract(brut));
        vente.setRemise(vente.getRemise().subtract(brut.subtract(net)));
        vente.setTotal(vente.getTotal().subtract(net));

        BigDecimal deduit = net.min(vente.getResteDu());
        BigDecimal rembourse = net.subtract(deduit);
        vente.setResteDu(vente.getResteDu().subtract(deduit));
        solde.setMontantBrut(brut);
        solde.setMontant(net);
        solde.setMontantDeduit(deduit);
        solde.setMontantRembourse(rembourse);
        if (rembourse.signum() > 0) {
            if (demande.modeRemboursement() == null) {
                throw new BusinessException("Choisissez comment rembourser le client");
            }
            solde.setSession(sessionsTravail.exiger(auteur, demande.sessionId()));
            solde.setModeRemboursement(demande.modeRemboursement());
            solde.setReferencePaiement(vide(demande.referencePaiement()));
        }
        solde.setNumero(numerotation.prochainNumero("AV"));
        return MateriauxDto.SoldeReponse.de(soldeRepository.save(solde));
    }

    /**
     * Solde saisi par erreur, annule par un gerant tant que la session de son
     * remboursement est ouverte : les especes n'ont pas encore ete comptees. La
     * marchandise redevient reservee au client, meme si elle a ete revendue entre-temps.
     */
    public MateriauxDto.SoldeReponse annulerSolde(Long id, String motif, UtilisateurPrincipal auteur) {
        reglages.exiger();
        SoldeReste solde = chargerSolde(id);
        if (solde.getStatut() == SoldeReste.Statut.ANNULE) {
            throw new BusinessException("Le solde " + solde.getNumero() + " est deja annule");
        }
        if (solde.getSession() != null && !solde.getSession().ouverte()) {
            throw new BusinessException("La session " + solde.getSession().getNumero()
                    + " est fermee : ce solde ne peut plus etre annule");
        }
        Vente vente = solde.getVente();
        for (LigneSoldeReste l : solde.getLignes()) {
            LigneVente ligne = l.getLigneVente();
            ligne.restaurer(l.getQuantite(), l.getQuantiteBase(), l.getRemise(), l.getTotal());
            stockService.reserverSansLimite(ligne.getArticle(), l.getQuantiteBase());
        }
        vente.setTotalBrut(vente.getTotalBrut().add(solde.getMontantBrut()));
        vente.setRemise(vente.getRemise().add(solde.getMontantBrut().subtract(solde.getMontant())));
        vente.setTotal(vente.getTotal().add(solde.getMontant()));
        vente.setResteDu(vente.getResteDu().add(solde.getMontantDeduit()));
        solde.setStatut(SoldeReste.Statut.ANNULE);
        solde.setAnnuleLe(Instant.now());
        solde.setAnnulePar(auteur.getNomComplet());
        solde.setMotifAnnulation(vide(motif));
        return MateriauxDto.SoldeReponse.de(solde);
    }

    @Transactional(readOnly = true)
    public List<MateriauxDto.SoldeReponse> soldesDeLaVente(Long venteId) {
        reglages.exiger();
        return soldeRepository.findByVenteIdOrderByDateSoldeDescIdDesc(venteId).stream().map(MateriauxDto.SoldeReponse::de).toList();
    }

    /** Soldes valides d'une vente, pour la mention portee sur sa facture. */
    @Transactional(readOnly = true)
    public List<SoldeReste> soldesValides(Long venteId) {
        return soldeRepository.findByVenteIdOrderByDateSoldeDescIdDesc(venteId).stream()
                .filter(s -> s.getStatut() == SoldeReste.Statut.VALIDE).toList();
    }

    @Transactional(readOnly = true)
    public SoldeReste chargerSolde(Long id) {
        return soldeRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Solde", id));
    }

    /** A l'encaissement, ce que le client emporte deja fait l'objet du premier bon. */
    @EventListener
    public void venteAvecResteALivrer(VenteService.VenteAvecResteALivrer evenement) {
        Vente vente = evenement.vente();
        BonLivraison bon = null;
        for (LigneVente ligne : vente.getLignes()) {
            if (ligne.getArticle().isSuiviStock() && ligne.getQuantiteLivree().signum() > 0) {
                if (bon == null) {
                    bon = nouveauBon(vente, vente.getCaissierNom());
                    bon.setDateBon(vente.getDateVente());
                }
                bon.ajouter(ligneBon(ligne, ligne.getQuantiteLivree(), ligne.getQuantiteBaseLivree()));
            }
        }
        if (bon != null) {
            repository.save(bon);
        }
    }

    /** La vente annulee remet elle-meme son stock : ses bons sont seulement marques. */
    @EventListener
    public void venteAnnulee(VenteService.VenteAnnulee evenement) {
        for (BonLivraison bon : repository.findByVenteIdOrderByDateBonDescIdDesc(evenement.venteId())) {
            if (bon.getStatut() == BonLivraison.Statut.VALIDE) {
                bon.setStatut(BonLivraison.Statut.ANNULE);
                bon.setAnnuleLe(Instant.now());
                bon.setAnnulePar(evenement.auteur());
                bon.setMotifAnnulation("Commande annulee");
            }
        }
    }

    private BonLivraison nouveauBon(Vente vente, String remisPar) {
        BonLivraison bon = new BonLivraison();
        bon.setNumero(numerotation.prochainNumero("BL"));
        bon.setVente(vente);
        bon.setDateBon(Instant.now());
        bon.setRemisPar(remisPar);
        return bon;
    }

    private static LigneBonLivraison ligneBon(LigneVente ligne, BigDecimal quantite, BigDecimal base) {
        LigneBonLivraison l = new LigneBonLivraison();
        l.setLigneVente(ligne);
        l.setQuantite(quantite);
        l.setQuantiteBase(base);
        l.setResteApres(ligne.resteALivrer());
        return l;
    }

    private static String vide(String texte) {
        return texte == null || texte.isBlank() ? null : texte.trim();
    }
}
