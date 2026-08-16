package tg.parcauto.api.gps.alerte;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AlerteGpsRepository extends JpaRepository<AlerteGps, Long> {

    /**
     * Deux requetes distinctes plutot qu'un critere optionnel : PostgreSQL ne sait
     * pas typer un parametre nul isole dans une clause {@code :param is null}.
     */
    @Query("""
            select a from AlerteGps a join fetch a.camion left join fetch a.zone
            order by a.horodatage desc
            """)
    Page<AlerteGps> toutes(Pageable pageable);

    @Query("""
            select a from AlerteGps a join fetch a.camion left join fetch a.zone
            where a.traitee = :traitee
            order by a.horodatage desc
            """)
    Page<AlerteGps> parEtat(@Param("traitee") boolean traitee, Pageable pageable);

    @Query("""
            select a from AlerteGps a join fetch a.camion left join fetch a.zone
            where a.traitee = false
            order by a.horodatage desc
            """)
    List<AlerteGps> nonTraitees(Pageable pageable);

    long countByTraiteeFalse();

    long countByTypeAndTraiteeFalse(TypeAlerteGps type);

    /** Evite de repeter la meme alerte a chaque position recue. */
    boolean existsByCamionIdAndTypeAndHorodatageAfter(Long camionId, TypeAlerteGps type, Instant depuis);

    boolean existsByCamionIdAndZoneIdAndTypeAndHorodatageAfter(Long camionId, Long zoneId,
                                                               TypeAlerteGps type, Instant depuis);
}
