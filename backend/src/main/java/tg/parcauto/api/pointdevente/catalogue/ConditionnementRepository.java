package tg.parcauto.api.pointdevente.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ConditionnementRepository extends JpaRepository<Conditionnement, Long> {

    List<Conditionnement> findByArticleIdOrderByCoefficientAsc(Long articleId);

    /** Conditionnements vendables : actifs, d'un produit actif. Charges une fois par la caisse. */
    @Query("""
            select c from Conditionnement c join fetch c.article a
            where c.actif = true and a.actif = true
            order by a.id, c.coefficient
            """)
    List<Conditionnement> vendables();

    Optional<Conditionnement> findByCodeBarres(String codeBarres);
}
