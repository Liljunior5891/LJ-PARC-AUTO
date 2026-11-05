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
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;
import tg.parcauto.api.pointdevente.catalogue.Conditionnement;
import tg.parcauto.api.pointdevente.vente.LigneVente;
import tg.parcauto.api.pointdevente.vente.Vente;
import tg.parcauto.api.pointdevente.vente.VenteDto;
import tg.parcauto.api.pointdevente.vente.VenteService;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Devis (factures pro forma) de la quincaillerie.
 * <p>
 * Un devis fige les prix du catalogue au jour de sa creation, avec remises,
 * jusqu'a sa date de validite. Il se modifie tant qu'il est en cours ;
 * accepte, il s'encaisse en une fois a ses prix et devient une vente (le
 * stock sort a ce moment). Expire, il doit etre prolonge avant d'etre encaisse.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DevisService {

    private final tg.parcauto.api.pointdevente.lieu.LieuStockService lieux;

    private final DevisRepository repository;
    private final ArticleRepository articleRepository;
    private final ReglageMateriauxService reglages;
    private final CompteClientService comptes;
    private final VenteService venteService;
    private final NumerotationService numerotation;

    @Transactional(readOnly = true)
    public Page<MateriauxDto.DevisReponse> rechercher(String q, Devis.Statut statut, Long clientId, Pageable pageable) {
        reglages.exiger();
        String motif = q == null ? "" : q.trim().toLowerCase();
        return repository.rechercher(motif, statut, clientId, pageable).map(d -> MateriauxDto.DevisReponse.de(d, false));
    }

    @Transactional(readOnly = true)
    public MateriauxDto.DevisReponse detail(Long id) {
        reglages.exiger();
        return MateriauxDto.DevisReponse.de(charger(id), true);
    }

    /** Devis complet, pour l'impression. */
    @Transactional(readOnly = true)
    public Devis entite(Long id) {
        reglages.exiger();
        return charger(id);
    }

    public MateriauxDto.DevisReponse creer(MateriauxDto.DevisRequete r, UtilisateurPrincipal vendeur) {
        ReglageMateriaux reglage = exigerDevis();
        Devis devis = new Devis();
        devis.setNumero(numerotation.prochainNumero("DEV"));
        devis.setDateDevis(LocalDate.now());
        devis.setVendeurNom(vendeur.getNomComplet());
        remplir(devis, r, reglage);
        return MateriauxDto.DevisReponse.de(repository.save(devis), true);
    }

    /** Modification complete d'un devis en cours : client, lignes, remise, validite. */
    public MateriauxDto.DevisReponse modifier(Long id, MateriauxDto.DevisRequete r) {
        ReglageMateriaux reglage = exigerDevis();
        Devis devis = enCours(id);
        devis.getLignes().clear();
        remplir(devis, r, reglage);
        return MateriauxDto.DevisReponse.de(devis, true);
    }

    /** Nouvelle date de validite, pour un devis expire que le client accepte encore. */
    public MateriauxDto.DevisReponse prolonger(Long id, LocalDate jusqua) {
        exigerDevis();
        Devis devis = enCours(id);
        if (jusqua == null || jusqua.isBefore(LocalDate.now())) {
            throw new BusinessException("La nouvelle date de validite doit etre aujourd'hui ou plus tard");
        }
        devis.setValableJusqu(jusqua);
        return MateriauxDto.DevisReponse.de(devis, true);
    }

    public MateriauxDto.DevisReponse annuler(Long id) {
        reglages.exiger();
        Devis devis = enCours(id);
        devis.setStatut(Devis.Statut.ANNULE);
        devis.setAnnuleLe(Instant.now());
        return MateriauxDto.DevisReponse.de(devis, true);
    }

    /**
     * Encaissement du devis accepte : une vente a ses prix, comptant ou a credit
     * pour un client a fiche. Le stock doit suffire a cet instant.
     */
    public VenteDto.Ticket encaisser(Long id, MateriauxDto.EncaissementDevis e, UtilisateurPrincipal caissier) {
        reglages.exiger();
        Devis devis = enCours(id);
        if (devis.expire()) {
            throw new BusinessException("Le devis " + devis.getNumero() + " a expire le " + devis.getValableJusqu()
                    + " : prolongez-le ou refaites-le aux prix du jour");
        }
        List<LigneVente> lignes = new ArrayList<>();
        java.util.Map<String, BigDecimal> emportes = new java.util.HashMap<>();
        if (e.emportes() != null) {
            e.emportes().forEach(x -> emportes.merge(x.articleId() + ":" + x.conditionnementId(), x.quantite(), BigDecimal::add));
        }
        for (LigneDevis l : devis.getLignes()) {
            LigneVente ligne = venteService.ligne(l.getArticle(), l.getConditionnement(), l.getQuantite(), l.getRemise(),
                    l.getPrixUnitaire());
            if (e.emportes() != null) {
                String cle = l.getArticle().getId() + ":"
                        + (l.getConditionnement() == null ? null : l.getConditionnement().getId());
                BigDecimal disponible = emportes.getOrDefault(cle, BigDecimal.ZERO);
                BigDecimal emporte = disponible.min(l.getQuantite());
                emportes.put(cle, disponible.subtract(emporte));
                venteService.emporter(ligne, emporte);
                e.emportes().stream()
                        .filter(x -> x.lieuId() != null && x.articleId().equals(l.getArticle().getId())
                                && java.util.Objects.equals(x.conditionnementId(),
                                        l.getConditionnement() == null ? null : l.getConditionnement().getId()))
                        .findFirst()
                        .ifPresent(x -> ligne.setLieu(lieux.demande(x.lieuId())));
            }
            lignes.add(ligne);
        }
        VenteDto.Reglement reglement = new VenteDto.Reglement(devis.getRemise(), e.modePaiement(), e.montantRecu(),
                e.referencePaiement(), devis.getClientNom(), e.sessionId());
        VenteDto.Compte compte = new VenteDto.Compte(devis.getClient() == null ? null : devis.getClient().getId(),
                Boolean.TRUE.equals(e.credit()), e.acompte());
        Vente vente = venteService.encaisserLignes(lignes, reglement, compte, caissier, devis.getId());
        devis.setStatut(Devis.Statut.CONVERTI);
        devis.setVenteId(vente.getId());
        return VenteDto.Ticket.de(vente);
    }

    /** La vente nee du devis est annulee : le devis redevient encaissable. */
    @EventListener
    public void venteAnnulee(VenteService.VenteDeDevisAnnulee evenement) {
        repository.findById(evenement.devisId())
                .filter(d -> d.getStatut() == Devis.Statut.CONVERTI && evenement.venteId().equals(d.getVenteId()))
                .ifPresent(d -> {
                    d.setStatut(Devis.Statut.EN_COURS);
                    d.setVenteId(null);
                });
    }

    // --------------------------------------------------------------- interne

    private ReglageMateriaux exigerDevis() {
        ReglageMateriaux reglage = reglages.exiger();
        if (!reglage.isDevisActif()) {
            throw new BusinessException("Les devis sont desactives dans les reglages");
        }
        return reglage;
    }

    private void remplir(Devis devis, MateriauxDto.DevisRequete r, ReglageMateriaux reglage) {
        ClientPdv client = r.clientId() == null ? null : comptes.client(r.clientId());
        String nom = client != null ? client.getNom() : vide(r.clientNom());
        if (nom == null) {
            throw new BusinessException("Indiquez le client du devis");
        }
        devis.setClient(client);
        devis.setClientNom(nom);
        devis.setNote(vide(r.note()));
        LocalDate validite = r.valableJusqu() != null ? r.valableJusqu()
                : devis.getDateDevis().plusDays(reglage.getDevisValiditeJours());
        if (validite.isBefore(devis.getDateDevis())) {
            throw new BusinessException("La date de validite precede la date du devis");
        }
        devis.setValableJusqu(validite);

        BigDecimal totalBrut = BigDecimal.ZERO;
        for (MateriauxDto.LigneDevisRequete demande : r.lignes()) {
            Article article = articleRepository.findById(demande.articleId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Article", demande.articleId()));
            Conditionnement conditionnement = demande.conditionnementId() == null ? null
                    : venteService.conditionnement(article, demande.conditionnementId());
            // Memes regles de prix et de remise qu'en caisse ; le devis garde le prix ainsi fixe.
            LigneVente modele = venteService.ligne(article, conditionnement, demande.quantite(), demande.remise(), null);
            LigneDevis ligne = new LigneDevis();
            ligne.setArticle(article);
            ligne.setConditionnement(conditionnement);
            ligne.setLibelle(modele.getLibelle());
            ligne.setPrixUnitaire(modele.getPrixUnitaire());
            ligne.setQuantite(modele.getQuantite());
            ligne.setRemise(modele.getRemise());
            ligne.setTotal(modele.getTotal());
            devis.ajouter(ligne);
            totalBrut = totalBrut.add(ligne.getTotal());
        }
        BigDecimal remise = r.remise() == null ? BigDecimal.ZERO : r.remise().setScale(2, RoundingMode.HALF_UP);
        if (remise.compareTo(totalBrut) > 0) {
            throw new BusinessException("La remise depasse le total du devis");
        }
        devis.setTotalBrut(totalBrut);
        devis.setRemise(remise);
        devis.setTotal(totalBrut.subtract(remise));
    }

    private Devis enCours(Long id) {
        Devis devis = charger(id);
        if (devis.getStatut() != Devis.Statut.EN_COURS) {
            throw new BusinessException("Le devis " + devis.getNumero() + " est "
                    + (devis.getStatut() == Devis.Statut.CONVERTI ? "deja transforme en vente" : "annule"));
        }
        return devis;
    }

    private Devis charger(Long id) {
        return repository.detail(id).orElseThrow(() -> ResourceNotFoundException.of("Devis", id));
    }

    private static String vide(String texte) {
        return texte == null || texte.isBlank() ? null : texte.trim();
    }
}
