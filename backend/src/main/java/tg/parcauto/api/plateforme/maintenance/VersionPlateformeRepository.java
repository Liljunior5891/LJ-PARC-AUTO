package tg.parcauto.api.plateforme.maintenance;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface VersionPlateformeRepository extends JpaRepository<VersionPlateforme, Long> {

    List<VersionPlateforme> findByPublieeLeLessThanEqualOrderByPublieeLeDescIdDesc(Instant maintenant, Pageable page);

    List<VersionPlateforme> findAllByOrderByPublieeLeDescIdDesc();

    boolean existsByNumeroIgnoreCase(String numero);

    boolean existsByNumeroIgnoreCaseAndIdNot(String numero, Long id);
}
