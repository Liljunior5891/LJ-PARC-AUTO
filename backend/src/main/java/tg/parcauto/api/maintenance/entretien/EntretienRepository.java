package tg.parcauto.api.maintenance.entretien;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EntretienRepository extends JpaRepository<Entretien, Long>,
        JpaSpecificationExecutor<Entretien> {

    /**
     * Interventions ouvertes, chargees avec leur camion : l'echeance se calcule
     * en memoire car elle combine un critere kilometrique et un critere calendaire.
     */
    @Query("""
            select e from Entretien e join fetch e.camion
            where e.statut in (tg.parcauto.api.maintenance.entretien.StatutEntretien.PLANIFIE,
                               tg.parcauto.api.maintenance.entretien.StatutEntretien.EN_COURS)
            order by e.datePrevue asc nulls last
            """)
    List<Entretien> ouverts();

    @Query("""
            select e from Entretien e join fetch e.camion
            where e.camion.id = :camionId
            order by coalesce(e.dateRealisation, e.datePrevue) desc nulls last
            """)
    List<Entretien> historiqueDuCamion(@Param("camionId") Long camionId);

    /** Derniere occurrence realisee d'un type d'entretien pour un camion. */
    @Query("""
            select e from Entretien e
            where e.camion.id = :camionId and e.type = :type
              and e.statut = tg.parcauto.api.maintenance.entretien.StatutEntretien.REALISE
            order by e.kilometrageRealise desc nulls last, e.dateRealisation desc nulls last
            limit 1
            """)
    Optional<Entretien> derniereRealisation(@Param("camionId") Long camionId,
                                            @Param("type") TypeEntretien type);

    long countByStatut(StatutEntretien statut);
}
