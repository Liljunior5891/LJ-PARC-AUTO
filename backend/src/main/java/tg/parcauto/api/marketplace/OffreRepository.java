package tg.parcauto.api.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OffreRepository extends JpaRepository<Offre, Long> {

    List<Offre> findAllByOrderByMiseEnAvantDescOrdreAscTitreAsc();

    List<Offre> findByPublieeTrueOrderByMiseEnAvantDescOrdreAscTitreAsc();

    boolean existsByArticleId(Long articleId);

    boolean existsByCamionId(Long camionId);
}
