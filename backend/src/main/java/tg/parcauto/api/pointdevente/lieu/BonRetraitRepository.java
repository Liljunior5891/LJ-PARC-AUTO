package tg.parcauto.api.pointdevente.lieu;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BonRetraitRepository extends JpaRepository<BonRetrait, Long> {

    List<BonRetrait> findByVenteIdOrderByIdAsc(Long venteId);

    Optional<BonRetrait> findByNumeroIgnoreCase(String numero);

    @Query(value = """
            select b from BonRetrait b join fetch b.lieu join fetch b.vente v
            where b.lieu.id in :lieux and b.statut in :statuts
              and (:q = '' or lower(b.numero) like concat('%', :q, '%') or lower(v.numero) like concat('%', :q, '%')
                   or lower(coalesce(v.clientNom, '')) like concat('%', :q, '%'))
            order by b.dateEmission
            """, countQuery = """
            select count(b) from BonRetrait b join b.vente v
            where b.lieu.id in :lieux and b.statut in :statuts
              and (:q = '' or lower(b.numero) like concat('%', :q, '%') or lower(v.numero) like concat('%', :q, '%')
                   or lower(coalesce(v.clientNom, '')) like concat('%', :q, '%'))
            """)
    Page<BonRetrait> recherche(@Param("lieux") Collection<Long> lieux, @Param("statuts") Collection<BonRetrait.Statut> statuts,
                               @Param("q") String q, Pageable pageable);

    /** Bons encore a servir et anciens : du stock bloque, que le gerant doit relancer. */
    @Query("select b from BonRetrait b join fetch b.lieu join fetch b.vente where b.statut = 'A_SERVIR' and b.dateEmission < :avant order by b.dateEmission")
    List<BonRetrait> anciens(@Param("avant") Instant avant);

    @Query("select b from BonRetrait b join fetch b.lieu join fetch b.vente where b.statut = 'A_SERVIR' and b.lieu.id in :lieux order by b.dateEmission desc")
    List<BonRetrait> aServir(@Param("lieux") Collection<Long> lieux);
}
