package tg.parcauto.api.pointdevente.associe;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface DepotAssocieRepository extends JpaRepository<DepotAssocie, Long> {

    List<DepotAssocie> findByAssocieIdOrderBySoumisLeDesc(Long associeId);

    List<DepotAssocie> findAllByOrderBySoumisLeDesc(Pageable pageable);

    List<DepotAssocie> findByStatutInOrderBySoumisLeAsc(Collection<DepotAssocie.Statut> statuts);

    long countByAssocieIdAndStatut(Long associeId, DepotAssocie.Statut statut);

    /** Total des depots d'un associe dans les statuts donnes. */
    @Query("""
            select coalesce(sum(d.montant), 0) from DepotAssocie d
            where d.associeId = :associe and d.statut in :statuts
            """)
    BigDecimal total(@Param("associe") Long associeId, @Param("statuts") Collection<DepotAssocie.Statut> statuts);

    /** Especes engagees (depots non annules) depuis le coffre d'un gerant. */
    @Query("""
            select coalesce(sum(d.montant), 0) from DepotAssocie d
            where d.coffreId = :gerant and d.source = tg.parcauto.api.pointdevente.associe.DepotAssocie.Source.COFFRE
              and d.statut <> tg.parcauto.api.pointdevente.associe.DepotAssocie.Statut.ANNULE
            """)
    BigDecimal sortiDuCoffre(@Param("gerant") Long gerantId);

    /** Depots engages (non annules) d'une source, du plus ancien au plus recent. */
    @Query("""
            select d from DepotAssocie d
            where d.source = :source and d.statut <> tg.parcauto.api.pointdevente.associe.DepotAssocie.Statut.ANNULE
            order by d.soumisLe
            """)
    List<DepotAssocie> engagesPar(@Param("source") DepotAssocie.Source source);
}
