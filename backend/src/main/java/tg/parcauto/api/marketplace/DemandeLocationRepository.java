package tg.parcauto.api.marketplace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tg.parcauto.api.exploitation.location.StatutLocation;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

public interface DemandeLocationRepository extends JpaRepository<DemandeLocation, Long> {

    Optional<DemandeLocation> findByJeton(String jeton);

    Page<DemandeLocation> findAllByOrderByDateDemandeDesc(Pageable pageable);

    Page<DemandeLocation> findByStatutOrderByDateDemandeDesc(DemandeLocation.Statut statut, Pageable pageable);

    long countByStatut(DemandeLocation.Statut statut);

    /** Vrai si un contrat dans l'un de ces etats occupe le vehicule sur une partie de la periode. */
    @Query("""
            select count(l) > 0 from Location l
            where l.camion.id = :camionId and l.statut in :statuts
              and l.dateDebut <= :fin and (l.dateFin is null or l.dateFin >= :debut)
            """)
    boolean vehiculeOccupe(@Param("camionId") Long camionId, @Param("debut") LocalDate debut,
                           @Param("fin") LocalDate fin, @Param("statuts") Collection<StatutLocation> statuts);
}
