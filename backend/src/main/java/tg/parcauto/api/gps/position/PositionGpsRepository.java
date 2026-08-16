package tg.parcauto.api.gps.position;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PositionGpsRepository extends JpaRepository<PositionGps, Long> {

    /** Derniere position connue d'un camion. */
    Optional<PositionGps> findFirstByCamionIdOrderByHorodatageDesc(Long camionId);

    /**
     * Derniere position de chaque camion equipe, pour la carte du parc (chapitre 23).
     * La sous-requete retient l'horodatage le plus recent par camion.
     */
    @Query("""
            select p from PositionGps p join fetch p.camion c
            where p.horodatage = (
                select max(q.horodatage) from PositionGps q where q.camion.id = p.camion.id
            )
            """)
    List<PositionGps> dernieresPositions();

    /** Trajet d'un camion sur une periode (chapitre 24). */
    @Query("""
            select p from PositionGps p
            where p.camion.id = :camionId and p.horodatage between :debut and :fin
            order by p.horodatage asc
            """)
    List<PositionGps> trajet(@Param("camionId") Long camionId,
                             @Param("debut") Instant debut,
                             @Param("fin") Instant fin);

    @Query("""
            select p from PositionGps p join fetch p.camion
            where p.camion.id = :camionId
            order by p.horodatage desc
            """)
    List<PositionGps> dernieres(@Param("camionId") Long camionId, Pageable pageable);

    long countByCamionIdAndHorodatageBetween(Long camionId, Instant debut, Instant fin);
}
