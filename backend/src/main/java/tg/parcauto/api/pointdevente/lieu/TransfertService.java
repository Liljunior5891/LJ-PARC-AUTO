package tg.parcauto.api.pointdevente.lieu;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;
import tg.parcauto.api.pointdevente.stock.StockService;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Transferts entre lieux : le gerant valide, le lieu de depart expedie, le lieu
 * d'arrivee receptionne. En transit, la marchandise reste dans le total de
 * l'entreprise ; un ecart a l'arrivee y reste jusqu'a la decision du gerant.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TransfertService {

    private final TransfertRepository repository;
    private final ArticleRepository articleRepository;
    private final LieuStockService lieux;
    private final StockService stockService;
    private final NumerotationService numerotation;

    public record LigneDemande(@NotNull Long articleId, @NotNull @Positive BigDecimal quantite) {
    }

    public record Demande(@NotNull Long sourceId, @NotNull Long destinationId, @Size(max = 255) String note,
                          @NotEmpty @Size(max = 200) List<@Valid LigneDemande> lignes) {
    }

    public record LigneSaisie(@NotNull Long ligneId, @NotNull @PositiveOrZero BigDecimal quantite) {
    }

    /** Quantites reellement chargees ; une ligne absente part en totalite. */
    public record Expedition(@Size(max = 150) String transporteur, List<@Valid LigneSaisie> lignes) {
    }

    /** Quantites reellement comptees ; une ligne absente arrive en totalite. */
    public record Reception(List<@Valid LigneSaisie> lignes) {
    }

    public record Annulation(@Size(max = 255) String motif) {
    }

    public record DecisionEcart(@NotNull LigneTransfert.DecisionEcart decision, @Size(max = 255) String motif) {
    }

    public record Ligne(Long id, Long articleId, String code, String nom, String unite, BigDecimal quantite,
                        BigDecimal quantiteExpediee, BigDecimal quantiteRecue, BigDecimal ecart,
                        LigneTransfert.DecisionEcart decisionEcart) {
        static Ligne de(LigneTransfert l) {
            Article a = l.getArticle();
            return new Ligne(l.getId(), a.getId(), a.getCode(), a.getNom(), a.getUnite(), l.getQuantite(),
                    l.getQuantiteExpediee(), l.getQuantiteRecue(), l.ecart(), l.getDecisionEcart());
        }
    }

    public record Reponse(Long id, String numero, Long sourceId, String sourceNom, Long destinationId, String destinationNom,
                          Transfert.Statut statut, String note, Instant demandeLe, String demandePar, Instant valideLe,
                          String validePar, Instant expedieLe, String expediePar, String transporteur, Instant recuLe,
                          String recuPar, Instant annuleLe, String annulePar, String motif, List<Ligne> lignes) {
        static Reponse de(Transfert t) {
            return new Reponse(t.getId(), t.getNumero(), t.getSource().getId(), t.getSource().getNom(), t.getDestination().getId(),
                    t.getDestination().getNom(), t.getStatut(), t.getNote(), t.getDemandeLe(), t.getDemandePar(), t.getValideLe(),
                    t.getValidePar(), t.getExpedieLe(), t.getExpediePar(), t.getTransporteur(), t.getRecuLe(), t.getRecuPar(),
                    t.getAnnuleLe(), t.getAnnulePar(), t.getMotif(), t.getLignes().stream().map(Ligne::de).toList());
        }
    }

    @Transactional(readOnly = true)
    public Page<Reponse> lister(boolean enCours, UtilisateurPrincipal u, Pageable pageable) {
        List<Long> permis = lieux.idsAccessibles(u);
        return permis.isEmpty() ? Page.empty(pageable) : repository.pour(permis, enCours, pageable).map(Reponse::de);
    }

    @Transactional(readOnly = true)
    public Reponse detail(Long id, UtilisateurPrincipal u) {
        Transfert t = charger(id);
        if (!lieux.accede(u, t.getSource()) && !lieux.accede(u, t.getDestination())) {
            throw new BusinessException("Ce transfert ne concerne pas vos lieux");
        }
        return Reponse.de(t);
    }

    @Transactional(readOnly = true)
    public Transfert charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Transfert", id));
    }

    public Reponse demander(Demande demande, UtilisateurPrincipal auteur) {
        LieuStock source = lieux.demande(demande.sourceId());
        LieuStock destination = lieux.demande(demande.destinationId());
        if (source == null || destination == null) {
            throw new BusinessException("Enregistrez d'abord un entrepot pour transferer du stock");
        }
        if (source.getId().equals(destination.getId())) {
            throw new BusinessException("Le lieu de depart et le lieu d'arrivee sont identiques");
        }
        if (!lieux.accede(auteur, source) && !lieux.accede(auteur, destination)) {
            throw new BusinessException("Ce transfert ne concerne pas vos lieux");
        }
        Transfert t = new Transfert();
        t.setNumero(numerotation.prochainNumero("TR"));
        t.setSource(source);
        t.setDestination(destination);
        t.setNote(vide(demande.note()));
        t.setDemandeLe(Instant.now());
        t.setDemandePar(auteur.getNomComplet());
        Set<Long> vus = new HashSet<>();
        for (LigneDemande l : demande.lignes()) {
            if (!vus.add(l.articleId())) {
                throw new BusinessException("Un article figure deux fois sur le transfert");
            }
            Article article = articleRepository.findById(l.articleId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Article", l.articleId()));
            if (!article.isSuiviStock()) {
                throw new BusinessException("Le stock de l'article " + article.getNom() + " n'est pas suivi");
            }
            LigneTransfert ligne = new LigneTransfert();
            ligne.setArticle(article);
            ligne.setQuantite(l.quantite());
            t.ajouter(ligne);
        }
        return Reponse.de(repository.save(t));
    }

    /** Validation par le gerant : rien ne bouge encore, le stock est verifie au depart. */
    public Reponse valider(Long id, UtilisateurPrincipal auteur) {
        Transfert t = charger(id);
        exigerStatut(t, Transfert.Statut.DEMANDE, "n'attend pas de validation");
        t.setStatut(Transfert.Statut.VALIDE);
        t.setValideLe(Instant.now());
        t.setValidePar(auteur.getNomComplet());
        return Reponse.de(t);
    }

    /** Refus d'une demande, ou annulation d'un transfert qui n'est pas encore parti. */
    public Reponse annuler(Long id, String motif, UtilisateurPrincipal auteur) {
        Transfert t = charger(id);
        if (t.getStatut() != Transfert.Statut.DEMANDE && t.getStatut() != Transfert.Statut.VALIDE) {
            throw new BusinessException("Le transfert " + t.getNumero() + " est deja parti : il ne s'annule plus, receptionnez-le");
        }
        t.setStatut(Transfert.Statut.ANNULE);
        t.setAnnuleLe(Instant.now());
        t.setAnnulePar(auteur.getNomComplet());
        t.setMotif(vide(motif));
        return Reponse.de(t);
    }

    /** La marchandise quitte le lieu de depart : ce qui est reserve a des clients ne peut pas partir. */
    public Reponse expedier(Long id, Expedition expedition, UtilisateurPrincipal auteur) {
        Transfert t = charger(id);
        lieux.exigerAcces(auteur, t.getSource());
        exigerStatut(t, Transfert.Statut.VALIDE, "doit d'abord etre valide par le gerant");
        Map<Long, LigneSaisie> saisies = parLigne(expedition == null ? null : expedition.lignes());
        boolean parti = false;
        for (LigneTransfert l : t.getLignes()) {
            LigneSaisie saisie = saisies.get(l.getId());
            BigDecimal quantite = saisie == null ? l.getQuantite() : saisie.quantite();
            if (quantite.compareTo(l.getQuantite()) > 0) {
                throw new BusinessException("La validation porte sur " + texte(l.getQuantite()) + " de " + l.getArticle().getNom());
            }
            l.setQuantiteExpediee(quantite);
            if (quantite.signum() > 0) {
                parti = true;
                stockService.sortirEnTransit(l.getArticle(), t.getSource(), quantite,
                        "Transfert " + t.getNumero() + " vers " + t.getDestination().getNom());
            }
        }
        if (!parti) {
            throw new BusinessException("Aucune quantite expediee : annulez plutot le transfert");
        }
        t.setStatut(Transfert.Statut.EXPEDIE);
        t.setExpedieLe(Instant.now());
        t.setExpediePar(auteur.getNomComplet());
        t.setTransporteur(expedition == null ? null : vide(expedition.transporteur()));
        return Reponse.de(t);
    }

    /** Le lieu d'arrivee compte : ce qui manque reste en transit jusqu'a la decision du gerant. */
    public Reponse receptionner(Long id, Reception reception, UtilisateurPrincipal auteur) {
        Transfert t = charger(id);
        lieux.exigerAcces(auteur, t.getDestination());
        exigerStatut(t, Transfert.Statut.EXPEDIE, "n'attend pas de reception");
        Map<Long, LigneSaisie> saisies = parLigne(reception == null ? null : reception.lignes());
        boolean ecart = false;
        for (LigneTransfert l : t.getLignes()) {
            LigneSaisie saisie = saisies.get(l.getId());
            BigDecimal quantite = saisie == null ? l.getQuantiteExpediee() : saisie.quantite();
            if (quantite.compareTo(l.getQuantiteExpediee()) > 0) {
                throw new BusinessException(texte(l.getQuantiteExpediee()) + " de " + l.getArticle().getNom()
                        + " ont ete expedies : un surplus se declare par un inventaire");
            }
            l.setQuantiteRecue(quantite);
            if (quantite.signum() > 0) {
                stockService.entrerDeTransit(l.getArticle(), t.getDestination(), quantite,
                        "Transfert " + t.getNumero() + " depuis " + t.getSource().getNom());
            }
            ecart = ecart || l.ecart().signum() > 0;
        }
        t.setRecuLe(Instant.now());
        t.setRecuPar(auteur.getNomComplet());
        t.setStatut(ecart ? Transfert.Statut.ECART : Transfert.Statut.RECU);
        return Reponse.de(t);
    }

    /** Le gerant tranche sur tout ce qui manque : perdu, revenu au depart, ou finalement arrive. */
    public Reponse traiterEcart(Long id, DecisionEcart decision, UtilisateurPrincipal auteur) {
        Transfert t = charger(id);
        exigerStatut(t, Transfert.Statut.ECART, "n'a pas d'ecart a traiter");
        String motif = "Ecart du transfert " + t.getNumero() + (vide(decision.motif()) == null ? "" : " : " + decision.motif().trim());
        for (LigneTransfert l : t.getLignes()) {
            BigDecimal ecart = l.ecart();
            if (ecart.signum() > 0) {
                switch (decision.decision()) {
                    case PERTE -> stockService.perdreEnTransit(l.getArticle(), t.getDestination(), ecart, motif);
                    case RETOUR_SOURCE -> stockService.entrerDeTransit(l.getArticle(), t.getSource(), ecart, motif);
                    case ARRIVE -> stockService.entrerDeTransit(l.getArticle(), t.getDestination(), ecart, motif);
                }
                l.setDecisionEcart(decision.decision());
            }
        }
        t.setMotif(vide(decision.motif()));
        t.setStatut(Transfert.Statut.RECU);
        return Reponse.de(t);
    }

    private static void exigerStatut(Transfert t, Transfert.Statut attendu, String message) {
        if (t.getStatut() != attendu) {
            throw new BusinessException("Le transfert " + t.getNumero() + " " + message);
        }
    }

    private static Map<Long, LigneSaisie> parLigne(List<LigneSaisie> lignes) {
        return lignes == null ? Map.of() : lignes.stream().collect(Collectors.toMap(LigneSaisie::ligneId, Function.identity(), (a, b) -> b));
    }

    private static String texte(BigDecimal quantite) {
        return quantite.stripTrailingZeros().toPlainString();
    }

    private static String vide(String texte) {
        return texte == null || texte.isBlank() ? null : texte.trim();
    }
}
