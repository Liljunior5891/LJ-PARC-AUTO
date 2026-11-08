package tg.parcauto.api.agence.operation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TransactionAgenceRepository extends JpaRepository<TransactionAgence, Long>,
        JpaSpecificationExecutor<TransactionAgence> {

    /** Pour une session de caisse : [nombre, cash encaisse, cash decaisse, commissions] des transactions validees. */
    @Query("""
            select count(t),
                   coalesce(sum(case when t.effetCash > 0 then t.montant else 0 end), 0),
                   coalesce(sum(case when t.effetCash < 0 then t.montant else 0 end), 0),
                   coalesce(sum(t.commission), 0)
            from TransactionAgence t
            where t.sessionId = :session and t.statut = tg.parcauto.api.agence.operation.TransactionAgence.Statut.VALIDEE
            """)
    List<Object[]> totauxSession(@Param("session") Long sessionId);

    /** Transactions validees identiques recentes, pour avertir d'un doublon. */
    @Query("""
            select t from TransactionAgence t
            where t.agenceId = :agence and t.serviceId = :service and t.typeOperationId = :type
              and t.montant = :montant and t.statut = tg.parcauto.api.agence.operation.TransactionAgence.Statut.VALIDEE
              and t.dateOperation >= :depuis
            order by t.dateOperation desc
            """)
    List<TransactionAgence> recentesIdentiques(@Param("agence") Long agenceId, @Param("service") Long serviceId,
                                               @Param("type") Long typeId, @Param("montant") long montant,
                                               @Param("depuis") Instant depuis);
}
