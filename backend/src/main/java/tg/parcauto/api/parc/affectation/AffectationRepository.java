package tg.parcauto.api.parc.affectation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AffectationRepository extends JpaRepository<Affectation, Long>,
        JpaSpecificationExecutor<Affectation> {

    @Query("select a from Affectation a join fetch a.chauffeur where a.camion.id = :camionId and a.dateFin is null")
    Optional<Affectation> affectationCouranteDuCamion(@Param("camionId") Long camionId);

    @Query("select a from Affectation a join fetch a.camion where a.chauffeur.id = :chauffeurId and a.dateFin is null")
    Optional<Affectation> affectationCouranteDuChauffeur(@Param("chauffeurId") Long chauffeurId);

    /**
     * Affectations du camion ou du chauffeur susceptibles de recouvrir la periode demandee.
     * Le recouvrement exact est verifie ensuite par {@link Affectation#chevauche}.
     */
    @Query("""
            select a from Affectation a
            join fetch a.camion join fetch a.chauffeur
            where (a.camion.id = :camionId or a.chauffeur.id = :chauffeurId)
              and (a.dateFin is null or a.dateFin >= :debut)
            """)
    List<Affectation> candidatsChevauchement(@Param("camionId") Long camionId,
                                             @Param("chauffeurId") Long chauffeurId,
                                             @Param("debut") LocalDate debut);

    @Query("select a from Affectation a join fetch a.camion join fetch a.chauffeur where a.dateFin is null")
    List<Affectation> affectationsEnCours();

    long countByDateFinIsNull();
}
