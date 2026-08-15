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
            where d.aChargeClient = false and d.dateDepense between :debut and :fin
            """)
    BigDecimal totalSurPeriode(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query("""
            select coalesce(sum(d.montant), 0) from Depense d
            where d.aChargeClient = false and d.camion.id = :camionId
              and d.dateDepense between :debut and :fin
            """)
    BigDecimal totalParCamion(@Param("camionId") Long camionId,
                              @Param("debut") LocalDate debut,
                              @Param("fin") LocalDate fin);

    @Query("""
            select d.camion.id as camionId, coalesce(sum(d.montant), 0) as total
            from Depense d
            where d.aChargeClient = false and d.dateDepense between :debut and :fin
            group by d.camion.id
            """)
    List<TotalParCamion> totauxParCamion(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    /** Depenses par categorie sur la periode (chapitre 17). */
    @Query("""
            select d.categorie as categorie, coalesce(sum(d.montant), 0) as total, count(d) as nombre
            from Depense d
            where d.aChargeClient = false and d.dateDepense between :debut and :fin
            group by d.categorie
            """)
    List<TotalParCategorie> totauxParCategorie(@Param("debut") LocalDate debut,
                                               @Param("fin") LocalDate fin);

    @Query("""
            select d.categorie as categorie, coalesce(sum(d.montant), 0) as total, count(d) as nombre
            from Depense d
            where d.aChargeClient = false and d.camion.id = :camionId
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
            where d.aChargeClient = false and d.camion.id = :camionId
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
}
