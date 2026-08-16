package tg.parcauto.api.parc.camion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarqueCamionRepository extends JpaRepository<MarqueCamion, Long> {

    List<MarqueCamion> findByActifTrueOrderByOrdreAscNomAsc();

    Optional<MarqueCamion> findByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCase(String nom);
}
