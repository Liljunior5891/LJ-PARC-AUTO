package tg.parcauto.api.finance.facture;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PaiementRepository extends JpaRepository<Paiement, Long> {

    List<Paiement> findByFactureIdOrderByDatePaiementAsc(Long factureId);

    @Query("""
            select coalesce(sum(p.montant), 0) from Paiement p
            where p.datePaiement between :debut and :fin
            """)
    BigDecimal totalEncaisseSurPeriode(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);
}
