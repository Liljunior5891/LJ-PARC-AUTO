package tg.parcauto.api.pointdevente.materiaux;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SoldeResteRepository extends JpaRepository<SoldeReste, Long> {

    /** Soldes d'une vente, du plus recent au plus ancien. */
    List<SoldeReste> findByVenteIdOrderByDateSoldeDescIdDesc(Long venteId);

    @Query("""
            select count(s) > 0 from SoldeReste s
            where s.vente.id = :venteId and s.statut = tg.parcauto.api.pointdevente.materiaux.SoldeReste.Statut.VALIDE
            """)
    boolean existeValidePourVente(@Param("venteId") Long venteId);

    /** [mode, total rembourse, nombre] des remboursements valides sortis d'une session. */
    @Query("""
            select s.modeRemboursement, coalesce(sum(s.montantRembourse), 0), count(s) from SoldeReste s
            where s.session.id = :sessionId and s.montantRembourse > 0
              and s.statut = tg.parcauto.api.pointdevente.materiaux.SoldeReste.Statut.VALIDE
            group by s.modeRemboursement
            """)
    List<Object[]> remboursementsSession(@Param("sessionId") Long sessionId);

    /** [date, numero, caisse, auteur, mode, montant rembourse] des remboursements valides de la periode (journaux). */
    @Query("""
            select s.dateSolde, s.numero, c.nom, s.auteurNom, s.modeRemboursement, s.montantRembourse from SoldeReste s
            left join s.session ss left join ss.caisse c
            where s.dateSolde >= :debut and s.dateSolde < :fin and s.montantRembourse > 0
              and s.statut = tg.parcauto.api.pointdevente.materiaux.SoldeReste.Statut.VALIDE
            order by s.dateSolde, s.id
            """)
    List<Object[]> journal(@Param("debut") java.time.Instant debut, @Param("fin") java.time.Instant fin);
}
