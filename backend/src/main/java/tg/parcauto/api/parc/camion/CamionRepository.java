package tg.parcauto.api.parc.camion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CamionRepository extends JpaRepository<Camion, Long>, JpaSpecificationExecutor<Camion> {

    Optional<Camion> findByImmatriculationIgnoreCase(String immatriculation);

    boolean existsByImmatriculationIgnoreCase(String immatriculation);

    /** Camion equipe de ce traceur GPS (chapitre 22). */
    Optional<Camion> findByGpsDeviceId(String gpsDeviceId);

    @Query("select c.statut as statut, count(c) as total from Camion c where c.actif = true group by c.statut")
    List<StatutCompte> compterParStatut();

    interface StatutCompte {
        StatutCamion getStatut();

        long getTotal();
    }
}
