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

    /**
     * Recettes qui engagent le vehicule sur une partie de la periode. La periode
     * d'une recette est la sienne si elle en a une, sinon celle de son contrat
     * de location, a defaut son jour.
     */
    @Query("""
            select r from Recette r left join r.location l left join r.mission m
            where r.camion.id = :camionId and r.type in :types and r.id <> :recetteExclue
              and coalesce(r.dateDebut, l.dateDebut, r.dateRecette) <= :fin
              and coalesce(r.dateFin, l.dateFin, r.dateDebut, l.dateDebut, r.dateRecette) >= :debut
              and (l is null or l.id <> :locationExclue)
              and (m is null or m.id <> :missionExclue)
            """)
    java.util.List<Recette> engagements(@Param("camionId") Long camionId,
                                        @Param("types") java.util.Collection<TypeRecette> types,
                                        @Param("debut") LocalDate debut, @Param("fin") LocalDate fin,
                                        @Param("recetteExclue") Long recetteExclue,
                                        @Param("locationExclue") Long locationExclue,
                                        @Param("missionExclue") Long missionExclue);

    /**
     * Contrats du vehicule qui chevauchent la periode, passes compris : un contrat
     * termine occupait bien le vehicule a ses dates. Seul un contrat annule libere.
     */
    @Query("""
            select l from Location l
            where l.camion.id = :camionId and l.id <> :locationExclue
              and l.statut <> tg.parcauto.api.exploitation.location.StatutLocation.ANNULEE
              and l.dateDebut <= :fin
              and (coalesce(l.dateFinReelle, l.dateFin) is null or coalesce(l.dateFinReelle, l.dateFin) >= :debut)
            """)
    java.util.List<tg.parcauto.api.exploitation.location.Location> contratsEngages(
            @Param("camionId") Long camionId, @Param("debut") LocalDate debut, @Param("fin") LocalDate fin,
            @Param("locationExclue") Long locationExclue);

    /**
     * Demandes en ligne acceptees qui reservent le vehicule sur une partie de la
     * periode, en attendant la venue du client (une demande expiree ne reserve plus).
     */
    @Query("""
            select d from tg.parcauto.api.marketplace.DemandeLocation d
            where d.camionId = :camionId and d.id <> :demandeExclue
              and d.statut = tg.parcauto.api.marketplace.DemandeLocation.Statut.ACCEPTEE
              and d.dateDebut >= CURRENT_DATE
              and d.dateDebut <= :fin and d.dateFin >= :debut
            """)
    java.util.List<tg.parcauto.api.marketplace.DemandeLocation> reservationsEnLigne(
            @Param("camionId") Long camionId, @Param("debut") LocalDate debut, @Param("fin") LocalDate fin,
            @Param("demandeExclue") Long demandeExclue);

    /** Missions du vehicule, sauf annulees (le chevauchement des dates se calcule ensuite). */
    @Query("""
            select m from Mission m
            where m.camion.id = :camionId and m.id <> :missionExclue
              and m.statut <> tg.parcauto.api.exploitation.mission.StatutMission.ANNULEE
            """)
    java.util.List<tg.parcauto.api.exploitation.mission.Mission> missionsEngagees(
            @Param("camionId") Long camionId, @Param("missionExclue") Long missionExclue);

    /** Total des recettes de contrats nes dans la boutique en ligne, sur la periode. */
    @Query("""
            select coalesce(sum(r.montant), 0) from Recette r left join r.location l
            where r.dateRecette between :debut and :fin and (r.enLigne = true or l.enLigne = true)
            """)
    BigDecimal totalEnLigneSurPeriode(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

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

    List<Recette> findByFactureId(Long factureId);

    List<Recette> findByLocationIdAndFactureIsNull(Long locationId);

    List<Recette> findByMissionIdAndFactureIsNull(Long missionId);
}
