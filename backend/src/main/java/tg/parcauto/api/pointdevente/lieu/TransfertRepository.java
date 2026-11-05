package tg.parcauto.api.pointdevente.lieu;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TransfertRepository extends JpaRepository<Transfert, Long> {

    @Query(value = """
            select t from Transfert t left join fetch t.source join fetch t.destination
            where (t.source.id in :lieux or t.destination.id in :lieux) and t.statut in :statuts
            order by t.demandeLe desc, t.id desc
            """, countQuery = """
            select count(t) from Transfert t
            where (t.source.id in :lieux or t.destination.id in :lieux) and t.statut in :statuts
            """)
    Page<Transfert> pour(@Param("lieux") Collection<Long> lieux, @Param("statuts") Collection<Transfert.Statut> statuts, Pageable pageable);

    /** Transferts qui engagent encore un lieu : il ne peut pas etre ferme. */
    @Query("""
            select count(t) from Transfert t where (t.source.id = :lieu or t.destination.id = :lieu)
              and t.statut in ('DEMANDE', 'VALIDE', 'EXPEDIE', 'ECART')
            """)
    long enCoursDuLieu(@Param("lieu") Long lieu);

    @Query("select t from Transfert t left join fetch t.source join fetch t.destination where t.statut in :statuts order by t.demandeLe")
    List<Transfert> parStatuts(@Param("statuts") Collection<Transfert.Statut> statuts);
}
