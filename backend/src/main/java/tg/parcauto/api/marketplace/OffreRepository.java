package tg.parcauto.api.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OffreRepository extends JpaRepository<Offre, Long> {

    List<Offre> findAllByOrderByMiseEnAvantDescOrdreAscTitreAsc();

    List<Offre> findByPublieeTrueOrderByMiseEnAvantDescOrdreAscTitreAsc();

    boolean existsByArticleId(Long articleId);

    boolean existsByCamionId(Long camionId);

    /** Retire du stock si assez reste, en une operation : 0 ligne touchee = stock insuffisant. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "update Offre o set o.stock = o.stock - :quantite where o.id = :id and o.stock >= :quantite")
    int retirerStock(@org.springframework.data.repository.query.Param("id") Long id,
                     @org.springframework.data.repository.query.Param("quantite") int quantite);
}
