package tg.parcauto.api.pointdevente.materiaux;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DepotClientRepository extends JpaRepository<DepotClient, Long> {

    Optional<DepotClient> findByNumero(String numero);

    List<DepotClient> findByClientIdOrderByDateOperationDesc(Long clientId);

    /** Operations d'un type et d'un statut, avec leur client charge (une seule requete). */
    @Query("select d from DepotClient d join fetch d.client where d.type = :type and d.statut = :statut order by d.dateOperation asc")
    List<DepotClient> enAttenteAvecClient(@Param("type") DepotClient.Type type, @Param("statut") DepotClient.Statut statut);

    /** Total des operations d'un client, d'un type et dans les statuts donnes. */
    @Query("""
            select coalesce(sum(d.montant), 0) from DepotClient d
            where d.client.id = :client and d.type = :type and d.statut in :statuts
            """)
    BigDecimal total(@Param("client") Long clientId, @Param("type") DepotClient.Type type,
                     @Param("statuts") Collection<DepotClient.Statut> statuts);

    /** [clientId, solde] : depots valides - remboursements valides ou en attente, par client. */
    @Query("""
            select d.client.id, coalesce(sum(case when d.type = tg.parcauto.api.pointdevente.materiaux.DepotClient.Type.DEPOT
                                                  then d.montant else -d.montant end), 0)
            from DepotClient d
            where d.client.id in :ids
              and (d.statut = tg.parcauto.api.pointdevente.materiaux.DepotClient.Statut.VALIDE
                   or (d.type = tg.parcauto.api.pointdevente.materiaux.DepotClient.Type.REMBOURSEMENT
                       and d.statut = tg.parcauto.api.pointdevente.materiaux.DepotClient.Statut.EN_ATTENTE))
            group by d.client.id
            """)
    List<Object[]> mouvementsNets(@Param("ids") Collection<Long> ids);

    /** [mode, total, nombre] des depots valides encaisses sur une session (totaux de la caisse). */
    @Query("""
            select d.modePaiement, coalesce(sum(d.montant), 0), count(d) from DepotClient d
            where d.session.id = :sessionId and d.type = tg.parcauto.api.pointdevente.materiaux.DepotClient.Type.DEPOT
              and d.statut = tg.parcauto.api.pointdevente.materiaux.DepotClient.Statut.VALIDE
            group by d.modePaiement
            """)
    List<Object[]> totauxSession(@Param("sessionId") Long sessionId);

    /** [date, numero, caisse, caissier, mode, montant, client] des depots valides de la periode (journaux). */
    @Query("""
            select d.dateOperation, d.numero, d.session.caisse.nom, d.auteurNom, d.modePaiement, d.montant, d.client.nom
            from DepotClient d
            where d.dateOperation >= :debut and d.dateOperation < :fin
              and d.type = tg.parcauto.api.pointdevente.materiaux.DepotClient.Type.DEPOT
              and d.statut = tg.parcauto.api.pointdevente.materiaux.DepotClient.Statut.VALIDE
            order by d.dateOperation, d.id
            """)
    List<Object[]> journalDepots(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /** Remboursements valides payes par la banque, jusqu'a une date (compte de l'entreprise). */
    @Query("""
            select d from DepotClient d
            where d.type = tg.parcauto.api.pointdevente.materiaux.DepotClient.Type.REMBOURSEMENT
              and d.source = tg.parcauto.api.pointdevente.materiaux.DepotClient.Source.BANQUE
              and d.statut = tg.parcauto.api.pointdevente.materiaux.DepotClient.Statut.VALIDE
              and d.traiteLe < :fin
            order by d.traiteLe
            """)
    List<DepotClient> remboursementsBanque(@Param("fin") Instant fin);
}
