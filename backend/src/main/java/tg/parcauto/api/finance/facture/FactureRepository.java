package tg.parcauto.api.finance.facture;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FactureRepository extends JpaRepository<Facture, Long>,
        JpaSpecificationExecutor<Facture> {

    @Query("""
            select f from Facture f
            join fetch f.client left join fetch f.lignes
            where f.id = :id
            """)
    Optional<Facture> chargerAvecLignes(@Param("id") Long id);

    /** Factures emises et non soldees (chapitre 28). */
    @Query("""
            select f from Facture f join fetch f.client
            where f.statut in (tg.parcauto.api.finance.facture.StatutFacture.NON_PAYEE,
                               tg.parcauto.api.finance.facture.StatutFacture.PARTIELLEMENT_PAYEE,
                               tg.parcauto.api.finance.facture.StatutFacture.EN_RETARD)
            order by f.dateEcheance asc nulls last
            """)
    List<Facture> impayees();

    @Query("""
            select coalesce(sum(f.montantTtc), 0) from Facture f
            where f.statut in (tg.parcauto.api.finance.facture.StatutFacture.NON_PAYEE,
                               tg.parcauto.api.finance.facture.StatutFacture.PARTIELLEMENT_PAYEE,
                               tg.parcauto.api.finance.facture.StatutFacture.EN_RETARD)
            """)
    BigDecimal totalFactureImpaye();

    @Query("""
            select coalesce(sum(p.montant), 0) from Paiement p
            where p.facture.statut in (tg.parcauto.api.finance.facture.StatutFacture.NON_PAYEE,
                                       tg.parcauto.api.finance.facture.StatutFacture.PARTIELLEMENT_PAYEE,
                                       tg.parcauto.api.finance.facture.StatutFacture.EN_RETARD)
            """)
    BigDecimal totalDejaRegleSurImpayees();

    /** Factures emises dont l'echeance est depassee et qui restent dues. */
    @Query("""
            select f from Facture f join fetch f.client
            where f.dateEcheance < :date
              and f.statut in (tg.parcauto.api.finance.facture.StatutFacture.NON_PAYEE,
                               tg.parcauto.api.finance.facture.StatutFacture.PARTIELLEMENT_PAYEE)
            """)
    List<Facture> echuesNonSoldees(@Param("date") LocalDate date);

    long countByStatut(StatutFacture statut);

    @Query("""
            select coalesce(sum(f.montantTtc), 0) from Facture f
            where f.statut <> tg.parcauto.api.finance.facture.StatutFacture.ANNULEE
              and f.statut <> tg.parcauto.api.finance.facture.StatutFacture.BROUILLON
              and f.dateEmission between :debut and :fin
            """)
    BigDecimal chiffreFactureSurPeriode(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);
}
