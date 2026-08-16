package tg.parcauto.api.maintenance.pneu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PneuRepository extends JpaRepository<Pneu, Long>, JpaSpecificationExecutor<Pneu> {

    boolean existsByReferenceIgnoreCase(String reference);

    @Query("""
            select p from Pneu p left join fetch p.camion
            where p.statut = tg.parcauto.api.maintenance.pneu.StatutPneu.MONTE
            """)
    List<Pneu> montes();

    @Query("""
            select p from Pneu p left join fetch p.camion
            where p.camion.id = :camionId
            order by p.statut asc, p.position asc
            """)
    List<Pneu> duCamion(@Param("camionId") Long camionId);

    @Query("""
            select p from Pneu p
            where p.camion.id = :camionId and p.position = :position
              and p.statut = tg.parcauto.api.maintenance.pneu.StatutPneu.MONTE
            """)
    Optional<Pneu> monteEnPosition(@Param("camionId") Long camionId,
                                   @Param("position") String position);

    long countByStatut(StatutPneu statut);
}
