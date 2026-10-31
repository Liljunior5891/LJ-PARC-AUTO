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
}
