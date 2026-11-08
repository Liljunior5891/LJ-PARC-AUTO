package tg.parcauto.api.agence.credit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface RemboursementAgenceRepository extends JpaRepository<RemboursementAgence, Long> {

    List<RemboursementAgence> findByCreditIdOrderByDateRemboursementDescIdDesc(Long creditId);

    /** Interets et frais financiers payes sur la periode : une charge. */
    @Query("""
            select coalesce(sum(r.interets), 0) from RemboursementAgence r, CreditAgence c
            where c.id = r.creditId and c.type <> tg.parcauto.api.agence.credit.CreditAgence.Type.AVANCE
              and r.agenceId in :agences and r.statut = tg.parcauto.api.agence.credit.RemboursementAgence.Statut.VALIDE
              and r.journee >= :du and r.journee <= :au
            """)
    long fraisFinanciers(@Param("agences") Collection<Long> agences, @Param("du") LocalDate du, @Param("au") LocalDate au);
}
