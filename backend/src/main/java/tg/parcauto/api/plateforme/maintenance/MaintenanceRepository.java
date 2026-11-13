package tg.parcauto.api.plateforme.maintenance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {

    /** Maintenances non annulees et pas encore finies : annoncees, a venir ou en cours. */
    @Query("""
            select m from Maintenance m
            where m.annuleeLe is null and m.finPrevue > :maintenant
              and (m.termineeLe is null or m.termineeLe > :maintenant)
            order by m.debut
            """)
    List<Maintenance> aVenirOuEnCours(@Param("maintenant") Instant maintenant);

    List<Maintenance> findTop100ByOrderByDebutDesc();
}
