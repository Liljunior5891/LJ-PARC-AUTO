package tg.parcauto.api.pointdevente.caisse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MouvementCaisseRepository extends JpaRepository<MouvementCaisse, Long> {

    List<MouvementCaisse> findBySessionIdOrderByDateMouvementAscIdAsc(Long sessionId);

    /** Entrees et sorties d'especes de la periode, toutes caisses (journal de caisse). */
    @org.springframework.data.jpa.repository.Query("""
            select m from MouvementCaisse m join fetch m.session s join fetch s.caisse
            where m.dateMouvement >= :debut and m.dateMouvement < :fin
            order by m.dateMouvement, m.id
            """)
    java.util.List<MouvementCaisse> periode(@org.springframework.data.repository.query.Param("debut") java.time.Instant debut,
                                            @org.springframework.data.repository.query.Param("fin") java.time.Instant fin);
}
