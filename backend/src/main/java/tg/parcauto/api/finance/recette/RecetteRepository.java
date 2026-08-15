package tg.parcauto.api.finance.recette;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface RecetteRepository extends JpaRepository<Recette, Long>,
        JpaSpecificationExecutor<Recette> {

    /** Total encaisse sur la periode, tous camions confondus. */
    @Query("""
            select coalesce(sum(r.montant), 0) from Recette r
            where r.dateRecette between :debut and :fin
            """)
    BigDecimal totalSurPeriode(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query("""
            select coalesce(sum(r.montant), 0) from Recette r
            where r.camion.id = :camionId and r.dateRecette between :debut and :fin
            """)
    BigDecimal totalParCamion(@Param("camionId") Long camionId,
                              @Param("debut") LocalDate debut,
                              @Param("fin") LocalDate fin);

    /** Recettes agregees par camion sur la periode (chapitres 29 et 30). */
    @Query("""
            select r.camion.id as camionId, coalesce(sum(r.montant), 0) as total
            from Recette r
            where r.dateRecette between :debut and :fin
            group by r.camion.id
            """)
    List<TotalParCamion> totauxParCamion(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    /**
     * Repartition par type de recette (chapitre 15).
     * Deux requetes distinctes plutot qu'un critere optionnel : PostgreSQL ne sait
     * pas typer un parametre nul isole dans une clause {@code :param is null}.
     */
    @Query("""
            select r.type as type, coalesce(sum(r.montant), 0) as total, count(r) as nombre
            from Recette r
            where r.dateRecette between :debut and :fin
            group by r.type
            """)
    List<TotalParType> totauxParType(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query("""
            select r.type as type, coalesce(sum(r.montant), 0) as total, count(r) as nombre
            from Recette r
            where r.dateRecette between :debut and :fin and r.camion.id = :camionId
            group by r.type
            """)
    List<TotalParType> totauxParTypePourCamion(@Param("camionId") Long camionId,
                                               @Param("debut") LocalDate debut,
                                               @Param("fin") LocalDate fin);

    boolean existsByFactureId(Long factureId);

    interface TotalParCamion {
        Long getCamionId();

        BigDecimal getTotal();
    }

    interface TotalParType {
        TypeRecette getType();

        BigDecimal getTotal();

        long getNombre();
    }
}
