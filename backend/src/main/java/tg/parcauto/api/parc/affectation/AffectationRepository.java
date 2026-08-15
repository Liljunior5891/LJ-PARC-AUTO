package tg.parcauto.api.parc.affectation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AffectationRepository extends JpaRepository<Affectation, Long> {

    @Query("""
            select a from Affectation a
            join fetch a.camion c join fetch a.chauffeur ch
            where (:camionId is null or c.id = :camionId)
              and (:chauffeurId is null or ch.id = :chauffeurId)
              and (:enCours is null or (:enCours = true and a.dateFin is null)
                   or (:enCours = false and a.dateFin is not null))
            """)
    Page<Affectation> rechercher(@Param("camionId") Long camionId,
                                 @Param("chauffeurId") Long chauffeurId,
                                 @Param("enCours") Boolean enCours,
                                 Pageable pageable);

    @Query("select a from Affectation a join fetch a.chauffeur where a.camion.id = :camionId and a.dateFin is null")
    Optional<Affectation> affectationCouranteDuCamion(@Param("camionId") Long camionId);

    @Query("select a from Affectation a join fetch a.camion where a.chauffeur.id = :chauffeurId and a.dateFin is null")
    Optional<Affectation> affectationCouranteDuChauffeur(@Param("chauffeurId") Long chauffeurId);

    @Query("""
            select a from Affectation a
            where (a.camion.id = :camionId or a.chauffeur.id = :chauffeurId)
              and (a.dateFin is null or a.dateFin >= :debut)
              and (cast(:fin as date) is null or a.dateDebut <= :fin)
            """)
    List<Affectation> chevauchements(@Param("camionId") Long camionId,
                                     @Param("chauffeurId") Long chauffeurId,
                                     @Param("debut") LocalDate debut,
                                     @Param("fin") LocalDate fin);

    @Query("select a from Affectation a join fetch a.camion join fetch a.chauffeur where a.dateFin is null")
    List<Affectation> affectationsEnCours();
}
