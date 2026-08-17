package tg.parcauto.api.parc.camion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TypeCamionRepository extends JpaRepository<TypeCamion, Long> {

    List<TypeCamion> findByActifTrueOrderByOrdreAscNomAsc();
}
