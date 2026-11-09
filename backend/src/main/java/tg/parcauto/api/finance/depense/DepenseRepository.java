package tg.parcauto.api.finance.depense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DepenseRepository extends JpaRepository<Depense, Long>,
        JpaSpecificationExecutor<Depense> {

    /**
     * Les depenses refacturees au client sont exclues de tous les totaux :
     * elles n'entament pas le resultat de l'entreprise (chapitre 11).
     */
    @Query("""
            select coalesce(sum(d.montant), 0) from Depense d
            where d.aChargeClient = false and d.statut = tg.parcauto.api.finance.depense.StatutDepense.VALIDEE and d.dateDepense between :debut and :fin
            """)
    BigDecimal totalSurPeriode(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query("""
            select coalesce(sum(d.montant), 0) from Depense d
            where d.aChargeClient = false and d.statut = tg.parcauto.api.finance.depense.StatutDepense.VALIDEE and d.camion.id = :camionId
              and d.dateDepense between :debut and :fin
            """)
    BigDecimal totalParCamion(@Param("camionId") Long camionId,
                              @Param("debut") LocalDate debut,
                              @Param("fin") LocalDate fin);

    @Query("""
            select d.camion.id as camionId, coalesce(sum(d.montant), 0) as total
            from Depense d
            where d.aChargeClient = false and d.statut = tg.parcauto.api.finance.depense.StatutDepense.VALIDEE and d.camion is not null
              and d.dateDepense between :debut and :fin
            group by d.camion.id
            """)
    List<TotalParCamion> totauxParCamion(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    /** Depenses par categorie sur la periode (chapitre 17). */
    @Query("""
            select d.categorie as categorie, coalesce(sum(d.montant), 0) as total, count(d) as nombre
            from Depense d
            where d.aChargeClient = false and d.statut = tg.parcauto.api.finance.depense.StatutDepense.VALIDEE and d.dateDepense between :debut and :fin
            group by d.categorie
            """)
    List<TotalParCategorie> totauxParCategorie(@Param("debut") LocalDate debut,
                                               @Param("fin") LocalDate fin);

    @Query("""
            select d.categorie as categorie, coalesce(sum(d.montant), 0) as total, count(d) as nombre
            from Depense d
            where d.aChargeClient = false and d.statut = tg.parcauto.api.finance.depense.StatutDepense.VALIDEE and d.camion.id = :camionId
              and d.dateDepense between :debut and :fin
            group by d.categorie
            """)
    List<TotalParCategorie> totauxParCategoriePourCamion(@Param("camionId") Long camionId,
                                                         @Param("debut") LocalDate debut,
                                                         @Param("fin") LocalDate fin);

    /** Detail par nature de depense, pour l'analyse fine du chapitre 17. */
    @Query("""
            select d.sousCategorie as sousCategorie, coalesce(sum(d.montant), 0) as total,
                   count(d) as nombre
            from Depense d
            where d.aChargeClient = false and d.statut = tg.parcauto.api.finance.depense.StatutDepense.VALIDEE and d.camion.id = :camionId
              and d.dateDepense between :debut and :fin
            group by d.sousCategorie
            """)
    List<TotalParNature> totauxParNaturePourCamion(@Param("camionId") Long camionId,
                                                   @Param("debut") LocalDate debut,
                                                   @Param("fin") LocalDate fin);

    /** Pleins de carburant d'un camion, dans l'ordre des compteurs (chapitre 18). */
    @Query("""
            select d from Depense d
            join fetch d.camion
            where d.camion.id = :camionId
              and d.categorie = tg.parcauto.api.finance.depense.CategorieDepense.CARBURANT
              and d.statut = tg.parcauto.api.finance.depense.StatutDepense.VALIDEE
              and d.quantiteLitres is not null and d.kilometrage is not null
              and d.dateDepense between :debut and :fin
            order by d.kilometrage asc, d.dateDepense asc
            """)
    List<Depense> pleinsDuCamion(@Param("camionId") Long camionId,
                                 @Param("debut") LocalDate debut,
                                 @Param("fin") LocalDate fin);

    interface TotalParCamion {
        Long getCamionId();

        BigDecimal getTotal();
    }

    interface TotalParCategorie {
        CategorieDepense getCategorie();

        BigDecimal getTotal();

        long getNombre();
    }

    interface TotalParNature {
        SousCategorieDepense getSousCategorie();

        BigDecimal getTotal();

        long getNombre();
    }

    /** Total des charges generales validees (sans vehicule) de la periode. */
    @Query("""
            select coalesce(sum(d.montant), 0) from Depense d
            where d.statut = tg.parcauto.api.finance.depense.StatutDepense.VALIDEE and d.camion is null and d.dateDepense between :debut and :fin
            """)
    BigDecimal totalChargesGenerales(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    /** [categorie id, nom, icone, total, nombre] des charges generales validees de la periode. */
    @Query("""
            select c.id, c.nom, c.icone, coalesce(sum(d.montant), 0), count(d) from Depense d join d.categorieCharge c
            where d.statut = tg.parcauto.api.finance.depense.StatutDepense.VALIDEE and d.dateDepense between :debut and :fin
            group by c.id, c.nom, c.icone order by sum(d.montant) desc
            """)
    List<Object[]> chargesParCategorie(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    long countByCategorieChargeId(Long categorieChargeId);

    long countByRecurrenteId(Long recurrenteId);

    long countByFournisseurParcId(Long fournisseurId);

    /** Mois deja payes ou en attente des depenses recurrentes : [recurrente id, periode]. */
    @Query("""
            select d.recurrente.id, d.periode from Depense d
            where d.recurrente is not null and d.statut in (tg.parcauto.api.finance.depense.StatutDepense.VALIDEE,
                  tg.parcauto.api.finance.depense.StatutDepense.ATTENTE_ADMIN)
            """)
    List<Object[]> moisPayes();

    @Query("""
            select count(d) > 0 from Depense d where d.recurrente.id = :id and d.periode = :periode
              and d.statut in (tg.parcauto.api.finance.depense.StatutDepense.VALIDEE,
                  tg.parcauto.api.finance.depense.StatutDepense.ATTENTE_ADMIN)
            """)
    boolean moisDejaPaye(@Param("id") Long recurrenteId, @Param("periode") String periode);

    /** Depenses qui attendent l'administrateur, les plus anciennes d'abord. */
    @Query("""
            select d from Depense d left join fetch d.camion left join fetch d.categorieCharge left join fetch d.fournisseurParc
            where d.statut = tg.parcauto.api.finance.depense.StatutDepense.ATTENTE_ADMIN
            order by d.creeLe, d.id
            """)
    List<Depense> enAttente();

    long countByStatut(StatutDepense statut);

    /** Depenses a payer validees (dettes), d'un fournisseur ou de tous. */
    @Query("""
            select d from Depense d left join fetch d.camion left join fetch d.categorieCharge left join fetch d.fournisseurParc
            where d.aPayer = true and d.statut = tg.parcauto.api.finance.depense.StatutDepense.VALIDEE
              and (:fournisseur is null or d.fournisseurParc.id = :fournisseur)
            order by d.echeance nulls last, d.dateDepense, d.id
            """)
    List<Depense> aPayer(@Param("fournisseur") Long fournisseurId);

    /** [fournisseur id, total des depenses validees] : les achats de chaque fournisseur. */
    @Query("""
            select d.fournisseurParc.id, coalesce(sum(d.montant), 0) from Depense d
            where d.fournisseurParc is not null and d.statut = tg.parcauto.api.finance.depense.StatutDepense.VALIDEE
            group by d.fournisseurParc.id
            """)
    List<Object[]> achatsParFournisseur();

    /** Depenses d'un fournisseur, les plus recentes d'abord. */
    @Query("""
            select d from Depense d left join fetch d.camion left join fetch d.categorieCharge
            where d.fournisseurParc.id = :fournisseur
            order by d.dateDepense desc, d.id desc
            """)
    List<Depense> duFournisseur(@Param("fournisseur") Long fournisseurId);
}
