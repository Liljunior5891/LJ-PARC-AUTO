package tg.parcauto.api.agence.tresorerie;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface LigneRegistreRepository extends JpaRepository<LigneRegistre, Long> {

    boolean existsByCompteId(Long compteId);

    List<LigneRegistre> findByEcritureIdOrderByIdAsc(Long ecritureId);

    /** Historique d'un compte, du plus recent au plus ancien. */
    @EntityGraph(attributePaths = "ecriture")
    Page<LigneRegistre> findByCompteIdOrderByIdDesc(Long compteId, Pageable pageable);

    /** Historique d'un compte sur une periode de journees de caisse. */
    @EntityGraph(attributePaths = "ecriture")
    Page<LigneRegistre> findByCompteIdAndJourneeBetweenOrderByIdDesc(Long compteId, LocalDate du, LocalDate au,
                                                                    Pageable pageable);

    /** Solde reconstitue a la fin d'une journee : somme des lignes jusqu'a cette journee incluse. */
    @Query("select coalesce(sum(l.montant), 0) from LigneRegistre l where l.compteId = :compte and l.journee <= :jour")
    long soldeAu(@Param("compte") Long compteId, @Param("jour") LocalDate jour);

    /**
     * Par compte : [compteId, ouverture, entrees, sorties, solde du registre, nombre de lignes].
     * Ouverture : lignes des ecritures d'ouverture ; entrees et sorties : les autres lignes.
     */
    @Query("""
            select l.compteId,
                   coalesce(sum(case when e.nature = :ouverture then l.montant else 0 end), 0),
                   coalesce(sum(case when e.nature <> :ouverture and l.montant > 0 then l.montant else 0 end), 0),
                   coalesce(sum(case when e.nature <> :ouverture and l.montant < 0 then -l.montant else 0 end), 0),
                   coalesce(sum(l.montant), 0),
                   count(l)
            from LigneRegistre l join l.ecriture e
            where l.compteId in :comptes
            group by l.compteId
            """)
    List<Object[]> syntheses(@Param("comptes") Collection<Long> compteIds,
                             @Param("ouverture") NatureEcriture ouverture);
}
