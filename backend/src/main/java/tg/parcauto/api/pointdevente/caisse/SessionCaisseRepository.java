package tg.parcauto.api.pointdevente.caisse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionCaisseRepository extends JpaRepository<SessionCaisse, Long> {

    Optional<SessionCaisse> findByCaissierIdAndStatut(Long caissierId, SessionCaisse.Statut statut);

    Page<SessionCaisse> findAllByOrderByOuverteLeDesc(Pageable pageable);

    long countByStatut(SessionCaisse.Statut statut);

    Optional<SessionCaisse> findByCaisseIdAndStatut(Long caisseId, SessionCaisse.Statut statut);

    Page<SessionCaisse> findByCaisseIdOrderByOuverteLeDesc(Long caisseId, Pageable pageable);

    long countByCaisseId(Long caisseId);

    java.util.List<SessionCaisse> findByOuverteLeGreaterThanEqualAndOuverteLeLessThanOrderByOuverteLeDesc(
            java.time.Instant debut, java.time.Instant fin);

    java.util.List<SessionCaisse> findByStatut(SessionCaisse.Statut statut);

    /** Verrou exclusif : fermeture et mouvements d'especes passent l'un apres l'autre. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from SessionCaisse s where s.id = :id")
    Optional<SessionCaisse> verrouiller(@org.springframework.data.repository.query.Param("id") Long id);

    /** Verrou partage : des ventes simultanees, mais aucune pendant la fermeture. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("select s from SessionCaisse s where s.id = :id")
    Optional<SessionCaisse> verrouillerPartage(@org.springframework.data.repository.query.Param("id") Long id);
}
