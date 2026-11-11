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

    /** Point qui precede, dans le temps de la mesure, celui que l'on recoit (meme arrive en retard). */
    Optional<PositionGps> findFirstByCamionIdAndHorodatageLessThanOrderByHorodatageDesc(Long camionId, Instant horodatage);

    /** Dernier point connu a cet instant ou avant. */
    Optional<PositionGps> findFirstByCamionIdAndHorodatageLessThanEqualOrderByHorodatageDesc(Long camionId, Instant horodatage);

    /** Points « en mouvement » (plus de 3 km/h) sur la periode. */
    @Query("""
            select count(p) from PositionGps p
            where p.camion.id = :camionId and p.horodatage > :debut and p.horodatage <= :fin and p.vitesseKmh > 3
            """)
    long compterEnMouvement(@Param("camionId") Long camionId, @Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("""
            select count(p) from PositionGps p
            where p.camion.id = :camionId and p.horodatage between :debut and :fin
            """)
    long compterTrajet(@Param("camionId") Long camionId, @Param("debut") Instant debut, @Param("fin") Instant fin);

    /** Un point sur {@code pas}, dans l'ordre : trajet allege quand la periode compte trop de points. */
    @Query(value = """
            select * from (
                select p.*, row_number() over (order by p.horodatage) as rang
                from position_gps p
                where p.camion_id = :camionId and p.horodatage between :debut and :fin
            ) t
            where mod(t.rang - 1, :pas) = 0
            order by t.horodatage
            """, nativeQuery = true)
    List<PositionGps> trajetEchantillonne(@Param("camionId") Long camionId, @Param("debut") Instant debut,
                                          @Param("fin") Instant fin, @Param("pas") long pas);
}
