package tg.parcauto.api.pointdevente.vente;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.caisse.SessionCaisse;
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;
import tg.parcauto.api.pointdevente.catalogue.Conditionnement;
import tg.parcauto.api.pointdevente.catalogue.ConditionnementRepository;
import tg.parcauto.api.pointdevente.materiaux.ClientPdv;
import tg.parcauto.api.pointdevente.materiaux.CompteClientService;
import tg.parcauto.api.pointdevente.materiaux.ReglageMateriauxService;
import tg.parcauto.api.pointdevente.materiaux.ReglementClientRepository;
import tg.parcauto.api.pointdevente.stock.StockService;
import tg.parcauto.api.pointdevente.stock.TypeMouvementStock;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Encaissement et annulation des ventes.
 * <p>
 * Tout est recalcule ici a partir du catalogue : le poste de caisse transmet
 * articles, quantites et remises, jamais un prix ni un total. Ticket, lignes et
 * sorties de stock sont ecrits dans une seule transaction : une rupture de
 * stock decouverte en cours de route annule l'ensemble.
 * <p>
 * En quincaillerie, une vente peut aussi etre rattachee a un compte client,
 * faite a credit ou avec acompte, et porter sur un conditionnement (tonne,
 * botte, carton) qui sort plusieurs unites de base du stock.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class VenteService {

    private final VenteRepository repository;
    private final tg.parcauto.api.pointdevente.caisse.SessionTravailService sessionsTravail;
    private final ArticleRepository articleRepository;
    private final StockService stockService;
    private final NumerotationService numerotation;
    private final ApplicationEventPublisher evenements;
    private final ConditionnementRepository conditionnementRepository;
    private final ReglageMateriauxService reglagesMateriaux;
    private final CompteClientService comptes;
    private final ReglementClientRepository reglementRepository;
    private final tg.parcauto.api.pointdevente.materiaux.SoldeResteRepository soldeRepository;
    private final tg.parcauto.api.pointdevente.lieu.LieuStockService lieux;
    private final tg.parcauto.api.pointdevente.caisse.PerimetreCaisse perimetre;

    public VenteDto.Ticket encaisser(VenteDto.Encaissement demande, UtilisateurPrincipal caissier) {
        List<LigneVente> lignes = new ArrayList<>();
        for (VenteDto.LigneDemande demandeLigne : demande.lignes()) {
            Article article = articleRepository.findById(demandeLigne.articleId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Article", demandeLigne.articleId()));
            Conditionnement conditionnement = demandeLigne.conditionnementId() == null ? null
                    : conditionnement(article, demandeLigne.conditionnementId());
            LigneVente ligne = ligne(article, conditionnement, demandeLigne.quantite(), demandeLigne.remise(), null);
            emporter(ligne, demandeLigne.quantiteLivree());
            tg.parcauto.api.pointdevente.lieu.LieuStock lieu = lieux.demande(demandeLigne.lieuId());
            if (lieu != null) {
                ligne.setLieu(lieu);
                // Lieu choisi sans quantite precise : tout se retirera avec le bon, sauf s'il s'agit du comptoir.
                if (demandeLigne.quantiteLivree() == null) {
                    emporter(ligne, BigDecimal.ZERO);
                }
            }
            if (demande.commandeEnLigneId() != null && ligne.resteALivrer().signum() > 0) {
                throw new BusinessException("Une commande en ligne se remet en une seule fois");
            }
            lignes.add(ligne);
        }
        Vente vente = encaisserLignes(lignes, demande.reglement(), demande.compte(), caissier, null);
        if (demande.commandeEnLigneId() != null) {
            // Commande en ligne remise et reglee : la boutique en ligne la clot (et peut refuser l'encaissement).
            vente.setEnLigne(true);
            evenements.publishEvent(new VenteEnLigneEncaissee(demande.commandeEnLigneId(), vente.getId()));
        }
        return VenteDto.Ticket.de(vente);
    }

    /**
     * Ligne de ticket au prix du catalogue, ou au prix garanti d'un devis
     * ({@code prixImpose} non nul). Un conditionnement (tonne, botte, carton)
     * a son propre prix et sort {@code coefficient} unites de base du stock.
     */
    public LigneVente ligne(Article article, Conditionnement conditionnement, BigDecimal quantite,
                            BigDecimal remise, BigDecimal prixImpose) {
        if (!article.isActif()) {
            throw new BusinessException("L'article " + article.getNom() + " n'est plus en vente");
        }
        BigDecimal coefficient = conditionnement == null ? BigDecimal.ONE : conditionnement.getCoefficient();
        BigDecimal prix = prixImpose != null ? prixImpose
                : conditionnement == null ? article.getPrixVente() : conditionnement.getPrixVente();
        BigDecimal montant = montant(prix.multiply(quantite));
        BigDecimal remiseLigne = montant(remise);
        if (remiseLigne.compareTo(montant) > 0) {
            throw new BusinessException("La remise sur " + article.getNom() + " depasse le montant de la ligne");
        }
        LigneVente ligne = new LigneVente();
        ligne.setArticle(article);
        ligne.setConditionnement(conditionnement);
        ligne.setLibelle(libelle(article, conditionnement));
        ligne.setPrixUnitaire(prix);
        ligne.setPrixAchat(article.getPrixAchat() == null ? null
                : montant(article.getPrixAchat().multiply(coefficient)));
        ligne.setQuantite(quantite);
        ligne.setQuantiteBase(quantite.multiply(coefficient));
        ligne.setQuantiteLivree(quantite);
        ligne.setQuantiteBaseLivree(ligne.getQuantiteBase());
        ligne.setRemise(remiseLigne);
        ligne.setTotal(montant.subtract(remiseLigne));
        return ligne;
    }

    /**
     * Ce que le client emporte de cette ligne ; le reste sera remis plus tard.
     * Un service, sans stock, n'a rien a remettre : il compte comme livre.
     */
    public void emporter(LigneVente ligne, BigDecimal emporte) {
        if (emporte == null || !ligne.getArticle().isSuiviStock()) {
            return;
        }
        if (emporte.signum() < 0 || emporte.compareTo(ligne.getQuantite()) > 0) {
            throw new BusinessException("La quantite emportee de " + ligne.getLibelle() + " depasse la quantite vendue");
        }
        ligne.fixerEmporte(emporte);
    }

    /** Libelle imprime : le produit, et son conditionnement s'il n'est pas vendu a l'unite de base. */
    public static String libelle(Article article, Conditionnement conditionnement) {
        String libelle = conditionnement == null ? article.getNom()
                : article.getNom() + " (" + conditionnement.getNom() + ")";
        return libelle.length() > 150 ? libelle.substring(0, 150) : libelle;
    }

    /** Conditionnement actif du produit, si les reglages autorisent la vente par conditionnement. */
    public Conditionnement conditionnement(Article article, Long conditionnementId) {
        if (!reglagesMateriaux.exiger().isConditionnementsActifs()) {
            throw new BusinessException("La vente par conditionnement est desactivee dans les reglages");
        }
        Conditionnement c = conditionnementRepository.findById(conditionnementId)
                .orElseThrow(() -> ResourceNotFoundException.of("Conditionnement", conditionnementId));
        if (!c.getArticle().getId().equals(article.getId())) {
            throw new BusinessException("Ce conditionnement n'est pas celui de " + article.getNom());
        }
        if (!c.isActif()) {
            throw new BusinessException("Le conditionnement " + c.getNom() + " de " + article.getNom()
                    + " n'est plus vendu");
        }
        return c;
    }

    /**
     * Enregistre un ticket et sort du stock, en unite de base, ce que le client
     * emporte ; le reste lui est reserve jusqu'a sa remise. {@code compte} porte le client et la vente a credit (quincaillerie) ;
     * {@code devisId} le devis accepte dont la vente est issue.
     */
    public Vente encaisserLignes(List<LigneVente> lignes, VenteDto.Reglement reglement, VenteDto.Compte compte,
                                 UtilisateurPrincipal caissier, Long devisId) {
        Vente vente = enregistrer(lignes, reglement, caissier, null, null, compte);
        vente.setDevisId(devisId);
        boolean resteALivrer = vente.getLignes().stream().anyMatch(l -> l.resteALivrer().signum() > 0);
        boolean surArrivage = false;
        boolean multiLieux = lieux.actif();
        tg.parcauto.api.pointdevente.lieu.LieuStock magasin = multiLieux ? lieux.lieuDeCaisse(vente.getSession().getCaisse()) : null;
        if (resteALivrer) {
            surArrivage = reglagesMateriaux.exiger().isVenteSurArrivage();
            // Avec des entrepots, le bon de retrait fait foi : son porteur est servi, avec ou sans fiche client.
            if (!multiLieux) {
                if (!reglagesMateriaux.exiger().isLivraisonDifferee()) {
                    throw new BusinessException("La livraison en plusieurs fois est desactivee dans les reglages");
                }
                if (vente.getClient() == null) {
                    throw new BusinessException("Choisissez la fiche du client a qui le reste sera livre");
                }
            }
        }
        for (LigneVente ligne : vente.getLignes()) {
            if (ligne.getArticle().isSuiviStock()) {
                if (multiLieux) {
                    if (ligne.getLieu() == null) {
                        ligne.setLieu(magasin);
                    }
                    if (!ligne.getLieu().getId().equals(magasin.getId()) && ligne.baseDejaLivree().signum() > 0) {
                        throw new BusinessException(ligne.getLibelle() + " se retire a " + ligne.getLieu().getNom()
                                + " : le client ne peut pas l'emporter depuis cette caisse");
                    }
                }
                if (ligne.baseDejaLivree().signum() > 0) {
                    stockService.appliquer(ligne.getArticle(), ligne.getLieu(), TypeMouvementStock.VENTE, ligne.baseDejaLivree(),
                            null, "Commande " + vente.getNumero(), vente.getId());
                }
                if (ligne.resteBaseALivrer().signum() > 0) {
                    if (surArrivage) {
                        stockService.reserverSansLimite(ligne.getArticle(), ligne.getLieu(), ligne.resteBaseALivrer());
                    } else {
                        stockService.reserver(ligne.getArticle(), ligne.getLieu(), ligne.resteBaseALivrer());
                    }
                }
            }
        }
        if (resteALivrer) {
            evenements.publishEvent(new VenteAvecResteALivrer(vente));
        }
        return vente;
    }

    /**
     * Ticket de l'addition d'une table. Les lignes viennent de la commande, a
     * ses prix ; le stock est deja sorti a l'envoi en cuisine et n'est pas
     * touche ici.
     */
    public Vente encaisserAddition(List<LigneVente> lignes, VenteDto.Reglement reglement,
                                   UtilisateurPrincipal caissier, Long commandeId, String tableNom) {
        return enregistrer(lignes, reglement, caissier, commandeId, tableNom, null);
    }

    private Vente enregistrer(List<LigneVente> lignes, VenteDto.Reglement reglement, UtilisateurPrincipal caissier,
                              Long commandeId, String tableNom, VenteDto.Compte compte) {
        // Pas de vente sans session ouverte par un gerant sur une caisse du vendeur.
        SessionCaisse session = sessionsTravail.exiger(caissier, reglement.sessionId());

        Vente vente = new Vente();
        vente.setSession(session);
        vente.setDateVente(Instant.now());
        vente.setCaissierNom(caissier.getNomComplet());
        vente.setClientNom(vide(reglement.clientNom()));
        vente.setCommandeId(commandeId);
        vente.setTableNom(tableNom);

        BigDecimal totalBrut = BigDecimal.ZERO;
        for (LigneVente ligne : lignes) {
            vente.ajouter(ligne);
            totalBrut = totalBrut.add(ligne.getTotal());
        }

        BigDecimal remise = montant(reglement.remise());
        if (remise.compareTo(totalBrut) > 0) {
            throw new BusinessException("La remise depasse le total de la commande");
        }
        BigDecimal total = totalBrut.subtract(remise);
        vente.setTotalBrut(totalBrut);
        vente.setRemise(remise);
        vente.setTotal(total);

        // Quincaillerie : client a fiche, et vente a credit ou avec acompte.
        BigDecimal aPayer = total;
        if (compte != null && (compte.clientId() != null || compte.credit())) {
            reglagesMateriaux.exiger();
            ClientPdv client = compte.clientId() == null ? null : comptes.client(compte.clientId());
            if (client != null) {
                vente.setClient(client);
                vente.setClientNom(client.getNom());
            }
            if (compte.credit()) {
                if (client == null) {
                    throw new BusinessException("Choisissez le client a qui la vente est faite a credit");
                }
                BigDecimal acompte = montant(compte.acompte());
                vente.setEcheance(comptes.autoriserCredit(client, total, acompte, caissier));
                vente.setResteDu(total.subtract(acompte));
                aPayer = acompte;
            }
        } else if (compte != null && reglagesMateriaux.estMateriaux()
                && reglagesMateriaux.courant().isClientObligatoire()) {
            throw new BusinessException("Choisissez le client : chaque vente doit etre rattachee a une fiche client");
        }
        vente.setMontantPaye(aPayer);
        regler(vente, reglement, aPayer);

        vente.setNumero(numerotation.prochainNumero("COM"));
        return repository.save(vente);
    }

    /** Annulation par un gerant : les articles reviennent en stock, le ticket reste consultable. */
    public VenteDto.Ticket annuler(Long id, String motif, UtilisateurPrincipal auteur) {
        Vente vente = charger(id);
        if (vente.getStatut() == Vente.Statut.ANNULEE) {
            throw new BusinessException("La commande " + vente.getNumero() + " est deja annulee");
        }
        if (reglementRepository.existeValidePourVente(vente.getId())) {
            throw new BusinessException("Le client a deja verse des reglements sur la commande " + vente.getNumero()
                    + " : annulez-les d'abord depuis son compte");
        }
        if (soldeRepository.existeValidePourVente(vente.getId())) {
            throw new BusinessException("Le reste de la commande " + vente.getNumero()
                    + " a ete solde : annulez d'abord ce solde depuis ses documents");
        }
        vente.setStatut(Vente.Statut.ANNULEE);
        vente.setAnnuleeLe(Instant.now());
        vente.setAnnuleePar(auteur.getNomComplet());
        vente.setMotifAnnulation(vide(motif));
        vente.setResteDu(BigDecimal.ZERO);
        if (vente.getDevisId() != null) {
            evenements.publishEvent(new VenteDeDevisAnnulee(vente.getId(), vente.getDevisId()));
        }
        if (vente.getCommandeId() != null) {
            // Addition d'une table : les plats ont ete servis, seul le reglement est defait.
            evenements.publishEvent(new AdditionAnnulee(vente.getId(), vente.getCommandeId()));
            return VenteDto.Ticket.de(vente);
        }
        boolean multiLieux = lieux.actif();
        if (multiLieux) {
            // Chaque quantite retourne dans le lieu d'ou elle est reellement sortie.
            stockService.retournerVente(vente.getId(), "Annulation de la commande " + vente.getNumero());
        }
        for (LigneVente ligne : vente.getLignes()) {
            if (ligne.getArticle().isSuiviStock()) {
                // Seul ce qui a ete remis revient en stock ; le reste n'etait que reserve.
                if (!multiLieux && ligne.baseDejaLivree().signum() > 0) {
                    stockService.appliquer(ligne.getArticle(), TypeMouvementStock.RETOUR, ligne.baseDejaLivree(),
                            null, "Annulation de la commande " + vente.getNumero(), vente.getId());
                }
                if (ligne.resteBaseALivrer().signum() > 0) {
                    stockService.liberer(ligne.getArticle(), ligne.getLieu(), ligne.resteBaseALivrer());
                }
            }
        }
        evenements.publishEvent(new VenteAnnulee(vente.getId(), auteur.getNomComplet()));
        return VenteDto.Ticket.de(vente);
    }

    @Transactional(readOnly = true)
    public VenteDto.Ticket ticket(Long id) {
        return VenteDto.Ticket.de(charger(id));
    }

    /** Ticket vu par un caissier : seulement s'il a ete encaisse sur sa caisse. */
    @Transactional(readOnly = true)
    public VenteDto.Ticket ticket(Long id, UtilisateurPrincipal utilisateur) {
        Vente vente = charger(id);
        perimetre.exiger(vente.getSession(), utilisateur);
        return VenteDto.Ticket.de(vente);
    }

    /** Vente complete, refusee a un caissier si elle vient d'une autre caisse. */
    @Transactional(readOnly = true)
    public Vente detail(Long id, UtilisateurPrincipal utilisateur) {
        Vente vente = charger(id);
        perimetre.exiger(vente.getSession(), utilisateur);
        return vente;
    }

    /** Vente complete (lignes et session chargees), pour la facture A4. */
    @Transactional(readOnly = true)
    public Vente detail(Long id) {
        return charger(id);
    }

    @Transactional(readOnly = true)
    public Page<VenteDto.Resume> lister(Instant debut, Instant fin, Long sessionId, Boolean enLigne,
                                        Pageable pageable) {
        return lister(debut, fin, sessionId, enLigne, null, null, pageable);
    }

    /**
     * Avec une session, {@code q} filtre sur le numero de commande, le nom ou le
     * telephone du client. Un caissier ne voit que les ventes de ses caisses.
     */
    @Transactional(readOnly = true)
    public Page<VenteDto.Resume> lister(Instant debut, Instant fin, Long sessionId, Boolean enLigne, String q,
                                        UtilisateurPrincipal utilisateur, Pageable pageable) {
        String recherche = q == null ? "" : q.trim().toLowerCase();
        java.util.Set<Long> caisses = perimetre.caisses(utilisateur);
        if (sessionId != null) {
            perimetre.exigerSession(sessionId, utilisateur);
        }
        Page<Vente> page = sessionId != null && !recherche.isEmpty()
                ? repository.deLaSession(sessionId, recherche, pageable)
                : sessionId != null
                ? repository.findBySessionIdOrderByDateVenteDesc(sessionId, pageable)
                : enLigne != null
                ? (caisses == null ? repository.periodeParCanal(debut, fin, enLigne, pageable)
                        : repository.periodeParCanalDesCaisses(debut, fin, enLigne, caisses, pageable))
                : caisses == null ? repository.periode(debut, fin, pageable)
                : repository.periodeDesCaisses(debut, fin, caisses, pageable);
        return page.map(VenteDto.Resume::de);
    }

    /** Ventes d'un client, les plus recentes d'abord. */
    @Transactional(readOnly = true)
    public Page<VenteDto.Resume> duClient(Long clientId, Pageable pageable) {
        reglagesMateriaux.exiger();
        return repository.findByClientIdOrderByDateVenteDesc(clientId, pageable).map(VenteDto.Resume::de);
    }

    /** Annulation du ticket d'une addition : la commande retrouve ses lignes a payer. */
    public record AdditionAnnulee(Long venteId, Long commandeId) {
    }

    /** Annulation d'une vente nee d'un devis : le devis redevient encaissable. */
    public record VenteDeDevisAnnulee(Long venteId, Long devisId) {
    }

    /**
     * Especes : le montant recu couvre ce qui est a payer maintenant (le total,
     * ou l'acompte d'une vente a credit) et la monnaie est calculee. Sinon,
     * paiement exact.
     */
    private void regler(Vente vente, VenteDto.Reglement demande, BigDecimal aPayer) {
        vente.setModePaiement(demande.modePaiement());
        vente.setReferencePaiement(vide(demande.referencePaiement()));
        if (demande.modePaiement() == ModePaiementPdv.ESPECES) {
            BigDecimal recu = demande.montantRecu() == null ? aPayer : montant(demande.montantRecu());
            if (recu.compareTo(aPayer) < 0) {
                throw new BusinessException("Montant recu insuffisant : il manque "
                        + aPayer.subtract(recu).stripTrailingZeros().toPlainString());
            }
            vente.setMontantRecu(recu);
            vente.setMonnaieRendue(recu.subtract(aPayer));
        } else {
            vente.setMontantRecu(aPayer);
            vente.setMonnaieRendue(BigDecimal.ZERO);
        }
    }

    private Vente charger(Long id) {
        return repository.detail(id).orElseThrow(() -> ResourceNotFoundException.of("Vente", id));
    }

    private static BigDecimal montant(BigDecimal valeur) {
        return valeur == null ? BigDecimal.ZERO.setScale(2) : valeur.setScale(2, RoundingMode.HALF_UP);
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    /** Vente dont une partie reste a remettre au client : ce qu'il emporte fait l'objet d'un premier bon. */
    public record VenteAvecResteALivrer(Vente vente) {
    }

    /** Vente annulee : ses bons de livraison le sont aussi. */
    public record VenteAnnulee(Long venteId, String auteur) {
    }

    /** Ticket encaisse pour une commande de la boutique en ligne. */
    public record VenteEnLigneEncaissee(Long commandeEnLigneId, Long venteId) {
    }
}
