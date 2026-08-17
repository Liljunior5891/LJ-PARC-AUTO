package tg.parcauto.api.parc.camion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CouleurCamionRepository extends JpaRepository<CouleurCamion, Long> {

    List<CouleurCamion> findByActifTrueOrderByOrdreAscNomAsc();
}
