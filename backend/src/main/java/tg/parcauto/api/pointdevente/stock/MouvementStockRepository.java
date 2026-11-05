package tg.parcauto.api.pointdevente.stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MouvementStockRepository extends JpaRepository<MouvementStock, Long> {

    /** Mouvements d'une vente, pour remettre chaque quantite la d'ou elle est sortie. */
    java.util.List<MouvementStock> findByVenteId(Long venteId);

    java.util.Optional<MouvementStock> findFirstByArticleIdAndMotifOrderByIdDesc(Long articleId, String motif);

    @EntityGraph(attributePaths = "article")
    Page<MouvementStock> findAllByOrderByDateMouvementDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = "article")
    Page<MouvementStock> findByArticleIdOrderByDateMouvementDescIdDesc(Long articleId, Pageable pageable);

    @EntityGraph(attributePaths = "article")
    java.util.List<MouvementStock> findByDateMouvementGreaterThanEqualAndDateMouvementLessThanOrderByDateMouvementDescIdDesc(
            java.time.Instant debut, java.time.Instant fin);

    /** Mouvements d'un produit sur une periode, du plus ancien au plus recent : la fiche de stock. */
    java.util.List<MouvementStock> findByArticleIdAndDateMouvementGreaterThanEqualAndDateMouvementLessThanOrderByDateMouvementAscIdAsc(
            Long articleId, java.time.Instant debut, java.time.Instant fin);

    /** Dernier mouvement d'un produit avant une date : il donne le stock a cette date. */
    java.util.Optional<MouvementStock> findFirstByArticleIdAndDateMouvementLessThanOrderByDateMouvementDescIdDesc(
            Long articleId, java.time.Instant date);
}
