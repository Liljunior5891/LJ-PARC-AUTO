package tg.parcauto.api.pointdevente.stock;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Seul point d'ecriture du stock : saisies manuelles, ventes et annulations
 * passent toutes par {@link #appliquer}, qui met a jour l'article et trace le
 * mouvement dans la meme transaction.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StockService {

    private final ArticleRepository articleRepository;
    private final MouvementStockRepository repository;

    public record Saisie(
            @NotNull Long articleId,
            @NotNull TypeMouvementStock type,
            /** Quantite deplacee, ou quantite comptee pour un inventaire. */
            @NotNull @PositiveOrZero BigDecimal quantite,
            @PositiveOrZero BigDecimal coutUnitaire,
            @Size(max = 255) String motif) {
    }

    /** Quantite comptee en rayon pour un article, lors d'un inventaire. */
    public record Comptage(@NotNull Long articleId, @NotNull @PositiveOrZero BigDecimal quantite) {
    }

    /** Feuille d'inventaire : tous les comptages valides ensemble. */
    public record Inventaire(
            @jakarta.validation.constraints.NotEmpty
            java.util.List<@jakarta.validation.Valid Comptage> comptages,
            @Size(max = 255) String motif) {
    }

    /** Bilan d'un inventaire : articles comptes, articles corriges et ecarts enregistres. */
    public record BilanInventaire(int comptes, int corriges, java.util.List<Reponse> ecarts) {
    }

    public record Reponse(
            Long id, Long articleId, String articleCode, String articleNom,
            TypeMouvementStock type, String typeLibelle, BigDecimal quantite, BigDecimal stockApres,
            BigDecimal coutUnitaire, Instant dateMouvement, String motif, Long venteId, String auteur) {

        public static Reponse de(MouvementStock m) {
            return new Reponse(
                    m.getId(), m.getArticle().getId(), m.getArticle().getCode(), m.getArticle().getNom(),
                    m.getType(), m.getType().getLibelle(), m.getQuantite(), m.getStockApres(),
                    m.getCoutUnitaire(), m.getDateMouvement(), m.getMotif(), m.getVenteId(), m.getCreePar());
        }
    }

    @Transactional(readOnly = true)
    public Page<Reponse> historique(Long articleId, Pageable pageable) {
        Page<MouvementStock> page = articleId == null
                ? repository.findAllByOrderByDateMouvementDescIdDesc(pageable)
                : repository.findByArticleIdOrderByDateMouvementDescIdDesc(articleId, pageable);
        return page.map(Reponse::de);
    }

    /** Saisie manuelle : reception, sortie, perte ou inventaire. */
    public Reponse saisir(Saisie saisie) {
        if (!saisie.type().isManuel()) {
            throw new BusinessException("Les mouvements de vente sont generes par la caisse");
        }
        Article article = articleRepository.findById(saisie.articleId())
                .orElseThrow(() -> ResourceNotFoundException.of("Article", saisie.articleId()));
        if (!article.isSuiviStock()) {
            throw new BusinessException("Le stock de l'article " + article.getNom() + " n'est pas suivi");
        }
        if (saisie.type() != TypeMouvementStock.INVENTAIRE && saisie.quantite().signum() == 0) {
            throw new BusinessException("La quantite doit etre superieure a zero");
        }
        MouvementStock mouvement = appliquer(article, saisie.type(), saisie.quantite(),
                saisie.coutUnitaire(), saisie.motif(), null);
        if (saisie.type() == TypeMouvementStock.ENTREE && saisie.coutUnitaire() != null) {
            // Le dernier cout de reception devient le cout de reference pour la marge.
            article.setPrixAchat(saisie.coutUnitaire());
        }
        return Reponse.de(mouvement);
    }

    /**
     * Valide une feuille d'inventaire en une fois : chaque article dont la
     * quantite comptee differe du stock recoit un mouvement d'inventaire ; les
     * articles justes ne laissent pas de trace inutile.
     */
    public BilanInventaire inventaire(Inventaire inventaire) {
        String motif = inventaire.motif() == null || inventaire.motif().isBlank()
                ? "Inventaire" : inventaire.motif().trim();
        java.util.List<Reponse> ecarts = new java.util.ArrayList<>();
        java.util.Set<Long> vus = new java.util.HashSet<>();
        for (Comptage comptage : inventaire.comptages()) {
            if (!vus.add(comptage.articleId())) {
                throw new BusinessException("Un article figure deux fois sur la feuille d'inventaire");
            }
            Article article = articleRepository.findById(comptage.articleId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Article", comptage.articleId()));
            if (!article.isSuiviStock()) {
                throw new BusinessException("Le stock de l'article " + article.getNom() + " n'est pas suivi");
            }
            if (article.getStockActuel().compareTo(comptage.quantite()) != 0) {
                ecarts.add(Reponse.de(appliquer(article, TypeMouvementStock.INVENTAIRE, comptage.quantite(),
                        null, motif, null)));
            }
        }
        return new BilanInventaire(inventaire.comptages().size(), ecarts.size(), ecarts);
    }

    /**
     * Applique un mouvement a un article. Une sortie ne peut pas rendre le stock
     * negatif : la caisse refuse de vendre ce qui n'existe pas en rayon. Une vente
     * ou une sortie ne peut pas non plus entamer ce qui est reserve pour un client ;
     * une perte ou un inventaire constatent une realite et restent possibles.
     */
    public MouvementStock appliquer(Article article, TypeMouvementStock type, BigDecimal quantite,
                                    BigDecimal coutUnitaire, String motif, Long venteId) {
        if ((type == TypeMouvementStock.VENTE || type == TypeMouvementStock.SORTIE)
                && article.stockDisponible().compareTo(quantite) < 0
                && article.getStockReserve().signum() > 0) {
            throw new BusinessException(insuffisant(article));
        }
        return ecrire(article, type, quantite, coutUnitaire, motif, venteId);
    }

    /** Vente non remise tout de suite : la marchandise reste en magasin, reservee au client. */
    public void reserver(Article article, BigDecimal quantite) {
        if (article.stockDisponible().compareTo(quantite) < 0) {
            throw new BusinessException(insuffisant(article));
        }
        article.setStockReserve(article.getStockReserve().add(quantite));
    }

    /**
     * Vente sur arrivage (reglage de l'administrateur) ou solde annule : la reservation
     * peut depasser le stock. Le disponible devient negatif et la remise attendra la reception.
     */
    public void reserverSansLimite(Article article, BigDecimal quantite) {
        article.setStockReserve(article.getStockReserve().add(quantite));
    }

    /** La vente est annulee : ce qui etait reserve redevient disponible. */
    public void liberer(Article article, BigDecimal quantite) {
        article.setStockReserve(article.getStockReserve().subtract(quantite).max(BigDecimal.ZERO));
    }

    /** Remise au client de marchandise reservee : le stock sort, la reservation diminue d'autant. */
    public MouvementStock livrer(Article article, BigDecimal quantite, String motif, Long venteId) {
        MouvementStock mouvement = ecrire(article, TypeMouvementStock.VENTE, quantite, null, motif, venteId);
        liberer(article, quantite);
        return mouvement;
    }

    /** Remise annulee : la marchandise revient en magasin et redevient reservee au client. */
    public MouvementStock annulerLivraison(Article article, BigDecimal quantite, String motif, Long venteId) {
        MouvementStock mouvement = ecrire(article, TypeMouvementStock.RETOUR, quantite, null, motif, venteId);
        article.setStockReserve(article.getStockReserve().add(quantite));
        return mouvement;
    }

    private static String insuffisant(Article article) {
        return "Stock insuffisant pour " + article.getNom() + " : "
                + article.stockDisponible().max(BigDecimal.ZERO).stripTrailingZeros().toPlainString() + " disponible(s)"
                + (article.getStockReserve().signum() > 0
                        ? ", " + article.getStockReserve().stripTrailingZeros().toPlainString() + " reserve(s) pour des clients"
                        : "");
    }

    private MouvementStock ecrire(Article article, TypeMouvementStock type, BigDecimal quantite,
                                  BigDecimal coutUnitaire, String motif, Long venteId) {
        BigDecimal avant = article.getStockActuel();
        BigDecimal apres = type == TypeMouvementStock.INVENTAIRE
                ? quantite
                : avant.add(quantite.multiply(BigDecimal.valueOf(type.getSens())));
        if (apres.signum() < 0) {
            throw new BusinessException("Stock insuffisant pour " + article.getNom()
                    + " : " + avant.stripTrailingZeros().toPlainString() + " disponible(s)");
        }
        article.setStockActuel(apres);

        MouvementStock mouvement = new MouvementStock();
        mouvement.setArticle(article);
        mouvement.setType(type);
        mouvement.setQuantite(apres.subtract(avant));
        mouvement.setStockApres(apres);
        mouvement.setCoutUnitaire(coutUnitaire);
        mouvement.setDateMouvement(Instant.now());
        mouvement.setMotif(motif == null || motif.isBlank() ? null : motif.trim());
        mouvement.setVenteId(venteId);
        return repository.save(mouvement);
    }
}
