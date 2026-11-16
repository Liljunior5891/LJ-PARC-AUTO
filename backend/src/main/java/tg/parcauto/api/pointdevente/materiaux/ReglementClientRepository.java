package tg.parcauto.api.pointdevente.materiaux;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ReglementClientRepository extends JpaRepository<ReglementClient, Long> {

    @Query("""
            select r from ReglementClient r join fetch r.vente join fetch r.session
            where r.client.id = :clientId order by r.dateReglement desc, r.id
            """)
    List<ReglementClient> duClient(@Param("clientId") Long clientId);

    @Query("""
            select r from ReglementClient r join fetch r.vente join fetch r.client join fetch r.session
            where r.numero = :numero order by r.id
            """)
    List<ReglementClient> parNumero(@Param("numero") String numero);

    @Query("""
            select count(r) > 0 from ReglementClient r
            where r.vente.id = :venteId and r.statut = tg.parcauto.api.pointdevente.materiaux.ReglementClient.Statut.VALIDE
            """)
    boolean existeValidePourVente(@Param("venteId") Long venteId);

    /** [mode, total, nombre de recus] des reglements valides encaisses sur une session. */
    @Query("""
            select r.modePaiement, coalesce(sum(r.montant), 0), count(distinct r.numero) from ReglementClient r
            where r.session.id = :sessionId
              and r.statut = tg.parcauto.api.pointdevente.materiaux.ReglementClient.Statut.VALIDE
            group by r.modePaiement
            """)
    List<Object[]> totauxSession(@Param("sessionId") Long sessionId);

    /** [clientId, total] des versements valides payes avec le compte client (argent depose). */
    @Query("""
            select r.client.id, coalesce(sum(r.montant), 0) from ReglementClient r
            where r.client.id in :ids
              and r.modePaiement = tg.parcauto.api.pointdevente.vente.ModePaiementPdv.COMPTE_CLIENT
              and r.statut = tg.parcauto.api.pointdevente.materiaux.ReglementClient.Statut.VALIDE
            group by r.client.id
            """)
    List<Object[]> payesParCompte(@Param("ids") java.util.Collection<Long> ids);

    /** Versements valides d'un client payes avec son compte, les plus recents d'abord. */
    @Query("""
            select r from ReglementClient r join fetch r.vente
            where r.client.id = :client
              and r.modePaiement = tg.parcauto.api.pointdevente.vente.ModePaiementPdv.COMPTE_CLIENT
              and r.statut = tg.parcauto.api.pointdevente.materiaux.ReglementClient.Statut.VALIDE
            order by r.dateReglement desc
            """)
    List<ReglementClient> versementsParCompte(@Param("client") Long clientId);

    /** Total des reglements valides recus sur une periode. */
    @Query("""
            select coalesce(sum(r.montant), 0) from ReglementClient r
            where r.dateReglement >= :debut and r.dateReglement < :fin
              and r.statut = tg.parcauto.api.pointdevente.materiaux.ReglementClient.Statut.VALIDE
            """)
    java.math.BigDecimal totalPeriode(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /** [date, numero, caisse, caissier, mode, montant, client] des reglements valides de la periode (journaux). */
    @Query("""
            select r.dateReglement, r.numero, r.session.caisse.nom, r.caissierNom, r.modePaiement, r.montant, r.client.nom
            from ReglementClient r
            where r.dateReglement >= :debut and r.dateReglement < :fin
              and r.statut = tg.parcauto.api.pointdevente.materiaux.ReglementClient.Statut.VALIDE
            order by r.dateReglement, r.id
            """)
    List<Object[]> journal(@Param("debut") Instant debut, @Param("fin") Instant fin);
}
