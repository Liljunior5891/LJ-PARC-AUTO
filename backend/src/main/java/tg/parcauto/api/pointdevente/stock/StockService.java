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
import tg.parcauto.api.pointdevente.lieu.LieuStock;
import tg.parcauto.api.pointdevente.lieu.LieuStockService;
import tg.parcauto.api.pointdevente.lieu.StockLieu;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final LieuStockService lieux;

    public record Saisie(
            @NotNull Long articleId,
            @NotNull TypeMouvementStock type,
            /** Quantite deplacee, ou quantite comptee pour un inventaire. */
            @NotNull @PositiveOrZero BigDecimal quantite,
            @PositiveOrZero BigDecimal coutUnitaire,
            @Size(max = 255) String motif,
            /** Lieu concerne, quand l'entreprise a des entrepots ; le magasin principal sinon. */
            Long lieuId) {

        public Saisie(Long articleId, TypeMouvementStock type, BigDecimal quantite, BigDecimal coutUnitaire, String motif) {
            this(articleId, type, quantite, coutUnitaire, motif, null);
        }
    }

    /** Quantite comptee en rayon pour un article, lors d'un inventaire. */
    public record Comptage(@NotNull Long articleId, @NotNull @PositiveOrZero BigDecimal quantite) {
    }

    /** Feuille d'inventaire : tous les comptages valides ensemble. */
    public record Inventaire(
            @jakarta.validation.constraints.NotEmpty
            java.util.List<@jakarta.validation.Valid Comptage> comptages,
            @Size(max = 255) String motif,
            /** Lieu compte, quand l'entreprise a des entrepots ; le magasin principal sinon. */
            Long lieuId) {

        public Inventaire(java.util.List<Comptage> comptages, String motif) {
            this(comptages, motif, null);
        }
    }

    /** Bilan d'un inventaire : articles comptes, articles corriges et ecarts enregistres. */
    public record BilanInventaire(int comptes, int corriges, java.util.List<Reponse> ecarts) {
    }

    public record Reponse(
            Long id, Long articleId, String articleCode, String articleNom,
            TypeMouvementStock type, String typeLibelle, BigDecimal quantite, BigDecimal stockApres,
            BigDecimal coutUnitaire, Instant dateMouvement, String motif, Long venteId, String auteur,
            Long lieuId, String lieuNom, BigDecimal stockLieuApres) {

        public static Reponse de(MouvementStock m) {
            return new Reponse(
                    m.getId(), m.getArticle().getId(), m.getArticle().getCode(), m.getArticle().getNom(),
                    m.getType(), m.getType().getLibelle(), m.getQuantite(), m.getStockApres(),
                    m.getCoutUnitaire(), m.getDateMouvement(), m.getMotif(), m.getVenteId(), m.getCreePar(),
                    m.getLieu() == null ? null : m.getLieu().getId(), m.getLieu() == null ? null : m.getLieu().getNom(),
                    m.getStockLieuApres());
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
        MouvementStock mouvement = appliquer(article, lieux.demande(saisie.lieuId()), saisie.type(), saisie.quantite(),
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
        LieuStock lieu = lieux.cible(lieux.demande(inventaire.lieuId()));
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
            BigDecimal enStock = lieu == null ? article.getStockActuel() : lieux.ligne(lieu, article).getStock();
            if (enStock.compareTo(comptage.quantite()) != 0) {
                ecarts.add(Reponse.de(appliquer(article, lieu, TypeMouvementStock.INVENTAIRE, comptage.quantite(),
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
        return appliquer(article, null, type, quantite, coutUnitaire, motif, venteId);
    }

    /** Meme chose dans un lieu ; sans lieu, le principal si le fonctionnement par lieu est actif. */
    public MouvementStock appliquer(Article article, LieuStock lieu, TypeMouvementStock type, BigDecimal quantite,
                                    BigDecimal coutUnitaire, String motif, Long venteId) {
        LieuStock cible = lieux.cible(lieu);
        if ((type == TypeMouvementStock.VENTE || type == TypeMouvementStock.SORTIE)) {
            boolean manque = cible == null
                    ? article.stockDisponible().compareTo(quantite) < 0 && article.getStockReserve().signum() > 0
                    : lieux.ligne(cible, article).disponible().compareTo(quantite) < 0;
            if (manque) {
                throw new BusinessException(insuffisant(article, cible));
            }
        }
        return ecrire(article, cible, type, quantite, coutUnitaire, motif, venteId);
    }

    /** Vente non remise tout de suite : la marchandise reste en magasin, reservee au client. */
    public void reserver(Article article, BigDecimal quantite) {
        reserver(article, null, quantite);
    }

    public void reserver(Article article, LieuStock lieu, BigDecimal quantite) {
        LieuStock cible = lieux.cible(lieu);
        BigDecimal disponible = cible == null ? article.stockDisponible() : lieux.ligne(cible, article).disponible();
        if (disponible.compareTo(quantite) < 0) {
            throw new BusinessException(insuffisant(article, cible));
        }
        reserverSansLimite(article, cible, quantite);
    }

    /**
     * Vente sur arrivage (reglage de l'administrateur) ou solde annule : la reservation
     * peut depasser le stock. Le disponible devient negatif et la remise attendra la reception.
     */
    public void reserverSansLimite(Article article, BigDecimal quantite) {
        reserverSansLimite(article, null, quantite);
    }

    public void reserverSansLimite(Article article, LieuStock lieu, BigDecimal quantite) {
        LieuStock cible = lieux.cible(lieu);
        if (cible != null) {
            StockLieu s = lieux.ligne(cible, article);
            s.setReserve(s.getReserve().add(quantite));
        }
        article.setStockReserve(article.getStockReserve().add(quantite));
    }

    /** La vente est annulee : ce qui etait reserve redevient disponible. */
    public void liberer(Article article, BigDecimal quantite) {
        liberer(article, null, quantite);
    }

    public void liberer(Article article, LieuStock lieu, BigDecimal quantite) {
        LieuStock cible = lieux.cible(lieu);
        if (cible != null) {
            StockLieu s = lieux.ligne(cible, article);
            s.setReserve(s.getReserve().subtract(quantite).max(BigDecimal.ZERO));
        }
        article.setStockReserve(article.getStockReserve().subtract(quantite).max(BigDecimal.ZERO));
    }

    /** Remise au client de marchandise reservee : le stock sort, la reservation diminue d'autant. */
    public MouvementStock livrer(Article article, BigDecimal quantite, String motif, Long venteId) {
        return livrer(article, null, quantite, motif, venteId);
    }

    public MouvementStock livrer(Article article, LieuStock lieu, BigDecimal quantite, String motif, Long venteId) {
        LieuStock cible = lieux.cible(lieu);
        MouvementStock mouvement = ecrire(article, cible, TypeMouvementStock.VENTE, quantite, null, motif, venteId);
        liberer(article, cible, quantite);
        return mouvement;
    }

    /** Remise annulee : la marchandise revient en magasin et redevient reservee au client. */
    public MouvementStock annulerLivraison(Article article, BigDecimal quantite, String motif, Long venteId) {
        return annulerLivraison(article, null, null, quantite, motif, venteId);
    }

    /** La marchandise revient la d'ou elle est sortie ; la reservation suit le lieu de retrait actuel de la ligne. */
    public MouvementStock annulerLivraison(Article article, LieuStock source, LieuStock lieuRetrait, BigDecimal quantite,
                                           String motif, Long venteId) {
        MouvementStock mouvement = ecrire(article, lieux.cible(source), TypeMouvementStock.RETOUR, quantite, null, motif, venteId);
        reserverSansLimite(article, lieuRetrait, quantite);
        return mouvement;
    }

    /** Le gerant change le lieu de retrait : le nouveau lieu doit avoir la marchandise disponible. */
    public void deplacerReservation(Article article, LieuStock depuis, LieuStock vers, BigDecimal quantite) {
        reserver(article, vers, quantite);
        liberer(article, depuis, quantite);
    }

    /**
     * Annulation d'une vente : chaque quantite retourne dans le lieu d'ou elle est
     * reellement sortie, lu sur ses mouvements (une vente peut avoir ete servie a
     * plusieurs endroits si son lieu de retrait a change).
     */
    public void retournerVente(Long venteId, String motif) {
        Map<String, BigDecimal> nets = new LinkedHashMap<>();
        Map<String, MouvementStock> exemples = new HashMap<>();
        for (MouvementStock m : repository.findByVenteId(venteId)) {
            LieuStock lieu = lieux.cible(m.getLieu());
            String cle = m.getArticle().getId() + "/" + (lieu == null ? "" : lieu.getId());
            nets.merge(cle, m.getQuantite(), BigDecimal::add);
            exemples.putIfAbsent(cle, m);
        }
        nets.forEach((cle, net) -> {
            if (net.signum() < 0) {
                MouvementStock m = exemples.get(cle);
                ecrire(m.getArticle(), lieux.cible(m.getLieu()), TypeMouvementStock.RETOUR, net.negate(), null, motif, venteId);
            }
        });
    }

    /** Lieu du dernier mouvement portant ce motif : une annulation y remet ce qui en est sorti. */
    @Transactional(readOnly = true)
    public LieuStock lieuDuMouvement(Article article, String motif) {
        return repository.findFirstByArticleIdAndMotifOrderByIdDesc(article.getId(), motif)
                .map(MouvementStock::getLieu).orElse(null);
    }

    /** Depart d'un transfert : la marchandise quitte le lieu mais reste dans le total de l'entreprise. */
    public void sortirEnTransit(Article article, LieuStock source, BigDecimal quantite, String motif) {
        StockLieu s = lieux.ligne(source, article);
        if (s.disponible().compareTo(quantite) < 0) {
            throw new BusinessException("Stock insuffisant pour " + article.getNom() + " a " + source.getNom() + " : "
                    + s.disponible().max(BigDecimal.ZERO).stripTrailingZeros().toPlainString() + " disponible(s)");
        }
        s.setStock(s.getStock().subtract(quantite));
        tracer(article, source, s, TypeMouvementStock.TRANSFERT_SORTIE, quantite.negate(), motif);
    }

    /** Arrivee d'un transfert, ou retour de la marchandise a son lieu de depart. */
    public void entrerDeTransit(Article article, LieuStock lieu, BigDecimal quantite, String motif) {
        StockLieu s = lieux.ligne(lieu, article);
        s.setStock(s.getStock().add(quantite));
        tracer(article, lieu, s, TypeMouvementStock.TRANSFERT_ENTREE, quantite, motif);
    }

    /** Marchandise perdue en route : elle n'etait plus dans aucun lieu, seul le total baisse. */
    public void perdreEnTransit(Article article, LieuStock lieu, BigDecimal quantite, String motif) {
        article.setStockActuel(article.getStockActuel().subtract(quantite).max(BigDecimal.ZERO));
        tracer(article, lieu, lieux.ligne(lieu, article), TypeMouvementStock.PERTE, quantite.negate(), motif);
    }

    private void tracer(Article article, LieuStock lieu, StockLieu s, TypeMouvementStock type, BigDecimal quantite, String motif) {
        MouvementStock mouvement = new MouvementStock();
        mouvement.setArticle(article);
        mouvement.setLieu(lieu);
        mouvement.setType(type);
        mouvement.setQuantite(quantite);
        mouvement.setStockApres(article.getStockActuel());
        mouvement.setStockLieuApres(s.getStock());
        mouvement.setDateMouvement(Instant.now());
        mouvement.setMotif(motif);
        repository.save(mouvement);
    }

    private String insuffisant(Article article, LieuStock lieu) {
        if (lieu != null) {
            StockLieu s = lieux.ligne(lieu, article);
            String autres = lieux.disponibilites(List.of(article.getId())).stream()
                    .filter(d -> !d.lieuId().equals(lieu.getId()) && d.disponible().signum() > 0)
                    .map(d -> d.lieuNom() + " : " + d.disponible().stripTrailingZeros().toPlainString())
                    .collect(Collectors.joining(", "));
            return "Stock insuffisant pour " + article.getNom() + " a " + lieu.getNom() + " : "
                    + s.disponible().max(BigDecimal.ZERO).stripTrailingZeros().toPlainString() + " disponible(s)"
                    + (autres.isEmpty() ? "" : ". Autres lieux : " + autres);
        }
        return "Stock insuffisant pour " + article.getNom() + " : "
                + article.stockDisponible().max(BigDecimal.ZERO).stripTrailingZeros().toPlainString() + " disponible(s)"
                + (article.getStockReserve().signum() > 0
                        ? ", " + article.getStockReserve().stripTrailingZeros().toPlainString() + " reserve(s) pour des clients"
                        : "");
    }

    private MouvementStock ecrire(Article article, LieuStock lieu, TypeMouvementStock type, BigDecimal quantite,
                                  BigDecimal coutUnitaire, String motif, Long venteId) {
        // Dans un lieu, c'est son stock qui ne peut pas passer sous zero ; le total de l'article suit de la meme variation.
        StockLieu ligne = lieu == null ? null : lieux.ligne(lieu, article);
        BigDecimal avant = ligne == null ? article.getStockActuel() : ligne.getStock();
        BigDecimal apres = type == TypeMouvementStock.INVENTAIRE
                ? quantite
                : avant.add(quantite.multiply(BigDecimal.valueOf(type.getSens())));
        if (apres.signum() < 0) {
            throw new BusinessException("Stock insuffisant pour " + article.getNom()
                    + (lieu == null ? "" : " a " + lieu.getNom())
                    + " : " + avant.stripTrailingZeros().toPlainString() + " disponible(s)");
        }
        BigDecimal variation = apres.subtract(avant);
        article.setStockActuel(article.getStockActuel().add(variation));
        if (ligne != null) {
            ligne.setStock(apres);
        }

        MouvementStock mouvement = new MouvementStock();
        mouvement.setArticle(article);
        mouvement.setLieu(lieu);
        mouvement.setType(type);
        mouvement.setQuantite(variation);
        mouvement.setStockApres(article.getStockActuel());
        mouvement.setStockLieuApres(ligne == null ? null : apres);
        mouvement.setCoutUnitaire(coutUnitaire);
        mouvement.setDateMouvement(Instant.now());
        mouvement.setMotif(motif == null || motif.isBlank() ? null : motif.trim());
        mouvement.setVenteId(venteId);
        return repository.save(mouvement);
    }
}
