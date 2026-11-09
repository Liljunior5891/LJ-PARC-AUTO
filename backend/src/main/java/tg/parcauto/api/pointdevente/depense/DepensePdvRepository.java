package tg.parcauto.api.pointdevente.depense;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DepensePdvRepository extends JpaRepository<DepensePdv, Long> {

    String FILTRE = """
            where d.dateDepense >= :debut and d.dateDepense <= :fin
              and (:categorieId is null or d.categorie.id = :categorieId)
              and (:mode is null or d.mode = :mode)
              and (:statut is null or d.statut = :statut)
              and (:q is null or lower(d.libelle) like :q or lower(coalesce(d.beneficiaire, '')) like :q
                   or lower(d.numero) like :q or lower(coalesce(d.reference, '')) like :q)
            """;

    @Query(value = "select d from DepensePdv d join fetch d.categorie left join fetch d.fournisseur left join fetch d.session "
            + FILTRE + " order by d.dateDepense desc, d.id desc",
            countQuery = "select count(d) from DepensePdv d " + FILTRE)
    Page<DepensePdv> rechercher(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin,
                                @Param("categorieId") Long categorieId, @Param("mode") ModeSortie mode,
                                @Param("statut") DepensePdv.Statut statut, @Param("q") String q, Pageable pageable);

    /** [categorie id, nom, icone, total, nombre] des depenses valides de la periode. */
    @Query("""
            select d.categorie.id, d.categorie.nom, d.categorie.icone, sum(d.montant), count(d) from DepensePdv d
            where d.dateDepense >= :debut and d.dateDepense <= :fin
              and d.statut = tg.parcauto.api.pointdevente.depense.DepensePdv.Statut.VALIDE
            group by d.categorie.id, d.categorie.nom, d.categorie.icone
            order by sum(d.montant) desc
            """)
    List<Object[]> parCategorie(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    /** [mode, total, nombre] des depenses valides de la periode. */
    @Query("""
            select d.mode, sum(d.montant), count(d) from DepensePdv d
            where d.dateDepense >= :debut and d.dateDepense <= :fin
              and d.statut = tg.parcauto.api.pointdevente.depense.DepensePdv.Statut.VALIDE
            group by d.mode
            """)
    List<Object[]> parMode(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query("""
            select coalesce(sum(d.montant), 0) from DepensePdv d
            where d.dateDepense >= :debut and d.dateDepense <= :fin
              and d.statut = tg.parcauto.api.pointdevente.depense.DepensePdv.Statut.VALIDE
            """)
    BigDecimal totalPeriode(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query("""
            select d from DepensePdv d join fetch d.categorie left join fetch d.fournisseur left join fetch d.session
            where d.dateDepense >= :debut and d.dateDepense <= :fin
            order by d.dateDepense desc, d.id desc
            """)
    List<DepensePdv> periode(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    /** Mois deja payes des depenses recurrentes : [recurrente id, periode]. */
    @Query("""
            select d.recurrente.id, d.periode from DepensePdv d
            where d.recurrente is not null and d.statut in (tg.parcauto.api.pointdevente.depense.DepensePdv.Statut.VALIDE,
                  tg.parcauto.api.pointdevente.depense.DepensePdv.Statut.ATTENTE_GERANT,
                  tg.parcauto.api.pointdevente.depense.DepensePdv.Statut.ATTENTE_ADMIN)
            """)
    List<Object[]> moisPayes();

    @Query("""
            select count(d) > 0 from DepensePdv d where d.recurrente.id = :id and d.periode = :periode
              and d.statut in (tg.parcauto.api.pointdevente.depense.DepensePdv.Statut.VALIDE,
                  tg.parcauto.api.pointdevente.depense.DepensePdv.Statut.ATTENTE_GERANT,
                  tg.parcauto.api.pointdevente.depense.DepensePdv.Statut.ATTENTE_ADMIN)
            """)
    boolean moisDejaPaye(@Param("id") Long recurrenteId, @Param("periode") String periode);

    long countByCategorieId(Long categorieId);

    long countByRecurrenteId(Long recurrenteId);

    /** Total des depenses valides payees depuis le coffre d'un gerant. */
    @Query("""
            select coalesce(sum(d.montant), 0) from DepensePdv d
            where d.coffreId = :gerant and d.mode = tg.parcauto.api.pointdevente.depense.ModeSortie.COFFRE
              and d.statut = tg.parcauto.api.pointdevente.depense.DepensePdv.Statut.VALIDE
            """)
    BigDecimal sortiDuCoffre(@Param("gerant") Long gerantId);

    /** Depenses valides d'un moyen de paiement, payees jusqu'a une date (journaux de banque et du coffre). */
    @Query("""
            select d from DepensePdv d join fetch d.categorie
            where d.mode = :mode and d.dateDepense <= :fin
              and d.statut = tg.parcauto.api.pointdevente.depense.DepensePdv.Statut.VALIDE
            order by d.dateDepense, d.id
            """)
    List<DepensePdv> payeesPar(@Param("mode") ModeSortie mode, @Param("fin") LocalDate fin);

    /** Depenses en attente d'une validation, les plus anciennes d'abord. */
    @Query("""
            select d from DepensePdv d join fetch d.categorie left join fetch d.fournisseur left join fetch d.session
            where d.statut in :statuts order by d.creeLe, d.id
            """)
    List<DepensePdv> enAttente(@Param("statuts") java.util.Collection<DepensePdv.Statut> statuts);
}
