package tg.parcauto.api.pointdevente.caisse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SortieAttenteRepository extends JpaRepository<SortieAttente, Long> {

    @Query("""
            select s from SortieAttente s join fetch s.session se join fetch se.caisse
            where s.statut in :statuts order by s.demandeeLe, s.id
            """)
    List<SortieAttente> parStatuts(@Param("statuts") Collection<SortieAttente.Statut> statuts);

    @Query("""
            select s from SortieAttente s join fetch s.session se join fetch se.caisse
            where s.statut in :statuts order by s.demandeeLe desc, s.id desc
            """)
    List<SortieAttente> recentes(@Param("statuts") Collection<SortieAttente.Statut> statuts,
                                 org.springframework.data.domain.Pageable pageable);

    List<SortieAttente> findBySessionIdOrderByDemandeeLeAscIdAsc(Long sessionId);

    /** Sorties d'un demandeur tranchees depuis une date (refusees ou approuvees). */
    @Query("""
            select s from SortieAttente s join fetch s.session se join fetch se.caisse
            where s.auteurId = :auteur and ((s.statut = tg.parcauto.api.pointdevente.caisse.SortieAttente.Statut.REFUSEE
                  and s.refuseeLe >= :depuis) or (s.statut = tg.parcauto.api.pointdevente.caisse.SortieAttente.Statut.VALIDEE
                  and s.approuveeLe >= :depuis))
            """)
    List<SortieAttente> trancheesPour(@Param("auteur") Long auteurId, @Param("depuis") java.time.Instant depuis);

    /** Especes deja demandees sur la session et pas encore tranchees. */
    @Query("""
            select coalesce(sum(s.montant), 0) from SortieAttente s where s.session.id = :sessionId
              and s.statut in (tg.parcauto.api.pointdevente.caisse.SortieAttente.Statut.ATTENTE_GERANT,
                               tg.parcauto.api.pointdevente.caisse.SortieAttente.Statut.ATTENTE_ADMIN)
            """)
    java.math.BigDecimal enAttenteSession(@Param("sessionId") Long sessionId);
}
