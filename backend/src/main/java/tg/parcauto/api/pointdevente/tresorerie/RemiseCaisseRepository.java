package tg.parcauto.api.pointdevente.tresorerie;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface RemiseCaisseRepository extends JpaRepository<RemiseCaisse, Long> {

    boolean existsBySessionIdAndStatut(Long sessionId, RemiseCaisse.Statut statut);

    Optional<RemiseCaisse> findFirstBySessionIdAndStatut(Long sessionId, RemiseCaisse.Statut statut);

    long countByStatut(RemiseCaisse.Statut statut);

    @Query("select r from RemiseCaisse r join fetch r.session s join fetch s.caisse where r.session.id = :id order by r.envoyeeLe desc")
    List<RemiseCaisse> deLaSession(@Param("id") Long sessionId);

    @Query("select r from RemiseCaisse r join fetch r.session s join fetch s.caisse where r.statut = :statut order by r.envoyeeLe")
    List<RemiseCaisse> parStatut(@Param("statut") RemiseCaisse.Statut statut);

    @Query(value = "select r from RemiseCaisse r join fetch r.session s join fetch s.caisse order by r.envoyeeLe desc",
            countQuery = "select count(r) from RemiseCaisse r")
    Page<RemiseCaisse> historique(Pageable pageable);

    /** Total recu dans le coffre d'un gerant. */
    @Query("""
            select coalesce(sum(r.montantValide), 0) from RemiseCaisse r
            where r.coffreId = :gerant and r.statut = tg.parcauto.api.pointdevente.tresorerie.RemiseCaisse.Statut.VALIDEE
            """)
    BigDecimal recuParCoffre(@Param("gerant") Long gerantId);

    @Query("""
            select r from RemiseCaisse r join fetch r.session s join fetch s.caisse
            where r.coffreId = :gerant and r.statut = tg.parcauto.api.pointdevente.tresorerie.RemiseCaisse.Statut.VALIDEE
            order by r.traiteeLe desc
            """)
    List<RemiseCaisse> recuesParCoffre(@Param("gerant") Long gerantId, Pageable pageable);

    /** Remises validees directement par l'administrateur : elles vont au compte de l'entreprise. */
    @Query("""
            select r from RemiseCaisse r join fetch r.session s join fetch s.caisse
            where r.coffreId is null and r.statut = tg.parcauto.api.pointdevente.tresorerie.RemiseCaisse.Statut.VALIDEE
            order by r.traiteeLe desc
            """)
    List<RemiseCaisse> verseesEntreprise(Pageable pageable);

    @Query("""
            select coalesce(sum(r.montantValide), 0) from RemiseCaisse r
            where r.coffreId is null and r.statut = tg.parcauto.api.pointdevente.tresorerie.RemiseCaisse.Statut.VALIDEE
            """)
    BigDecimal totalVerseEntreprise();

    /** Gerants qui ont deja recu une remise : ceux dont le coffre a un historique. */
    @Query("select distinct r.coffreId, r.traiteeParNom from RemiseCaisse r where r.coffreId is not null")
    List<Object[]> coffres();
}
