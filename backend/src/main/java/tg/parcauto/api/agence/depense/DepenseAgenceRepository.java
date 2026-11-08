package tg.parcauto.api.agence.depense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface DepenseAgenceRepository extends JpaRepository<DepenseAgence, Long>, JpaSpecificationExecutor<DepenseAgence> {

    /** Une charge recurrente deja reglee pour ce mois. */
    @Query("""
            select count(d) > 0 from DepenseAgence d
            where d.recurrenteId = :recurrente and d.periode = :periode
              and d.statut = tg.parcauto.api.agence.depense.DepenseAgence.Statut.VALIDEE
            """)
    boolean dejaReglee(@Param("recurrente") Long recurrenteId, @Param("periode") String periode);

    /** Total des depenses validees des agences sur une periode de journees. */
    @Query("""
            select coalesce(sum(d.montant), 0) from DepenseAgence d
            where d.agenceId in :agences and d.statut = tg.parcauto.api.agence.depense.DepenseAgence.Statut.VALIDEE
              and d.journee >= :du and d.journee <= :au
            """)
    long total(@Param("agences") Collection<Long> agences, @Param("du") LocalDate du, @Param("au") LocalDate au);

    /** [categorieId, total, nombre] des depenses validees de la periode. */
    @Query("""
            select d.categorieId, coalesce(sum(d.montant), 0), count(d) from DepenseAgence d
            where d.agenceId in :agences and d.statut = tg.parcauto.api.agence.depense.DepenseAgence.Statut.VALIDEE
              and d.journee >= :du and d.journee <= :au
            group by d.categorieId
            """)
    List<Object[]> parCategorie(@Param("agences") Collection<Long> agences, @Param("du") LocalDate du, @Param("au") LocalDate au);

    /** [journee, total] des depenses validees, pour les series par jour et par mois. */
    @Query("""
            select d.journee, coalesce(sum(d.montant), 0) from DepenseAgence d
            where d.agenceId in :agences and d.statut = tg.parcauto.api.agence.depense.DepenseAgence.Statut.VALIDEE
              and d.journee >= :du and d.journee <= :au
            group by d.journee
            """)
    List<Object[]> parJour(@Param("agences") Collection<Long> agences, @Param("du") LocalDate du, @Param("au") LocalDate au);
}
