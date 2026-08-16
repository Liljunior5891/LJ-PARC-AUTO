package tg.parcauto.api.maintenance.incident;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long>,
        JpaSpecificationExecutor<Incident> {

    @Query("""
            select i from Incident i join fetch i.camion left join fetch i.chauffeur
            where i.statut not in (tg.parcauto.api.maintenance.incident.StatutIncident.RESOLU,
                                   tg.parcauto.api.maintenance.incident.StatutIncident.CLOS)
            order by i.survenuLe desc
            """)
    List<Incident> ouverts();

    @Query("""
            select i from Incident i join fetch i.camion left join fetch i.chauffeur
            order by i.survenuLe desc
            """)
    Page<Incident> tous(Pageable pageable);

    long countByStatutIn(java.util.Collection<StatutIncident> statuts);

    long countByCamionIdAndStatutIn(Long camionId, java.util.Collection<StatutIncident> statuts);
}
