package tg.parcauto.api.pointdevente.horsligne;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VenteHorsLignePdvRepository extends JpaRepository<VenteHorsLignePdv, Long> {

    Optional<VenteHorsLignePdv> findByCle(String cle);

    List<VenteHorsLignePdv> findByStatutOrderBySaisieLeAsc(VenteHorsLignePdv.Statut statut);

    long countByStatut(VenteHorsLignePdv.Statut statut);

    /** Ventes hors ligne recentes, conflits compris, les plus recentes d'abord. */
    @Query("""
            select v from VenteHorsLignePdv v where v.saisieLe >= :depuis
            order by v.saisieLe desc, v.id desc
            """)
    List<VenteHorsLignePdv> depuis(@Param("depuis") Instant depuis);

    /** Ventes enregistrees avec quelque chose a regulariser (stock manquant, prix different). */
    @Query("""
            select v from VenteHorsLignePdv v
            where v.saisieLe >= :depuis and (v.manques is not null or v.ecartsPrix is not null)
              and v.statut in (tg.parcauto.api.pointdevente.horsligne.VenteHorsLignePdv.Statut.ENREGISTREE,
                               tg.parcauto.api.pointdevente.horsligne.VenteHorsLignePdv.Statut.REPRISE)
            order by v.saisieLe desc
            """)
    List<VenteHorsLignePdv> aRegulariser(@Param("depuis") Instant depuis);
}
