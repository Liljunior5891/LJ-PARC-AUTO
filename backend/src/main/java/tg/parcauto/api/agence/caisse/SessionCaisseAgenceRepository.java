package tg.parcauto.api.agence.caisse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SessionCaisseAgenceRepository extends JpaRepository<SessionCaisseAgence, Long> {

    Optional<SessionCaisseAgence> findByCaissierIdAndStatut(Long caissierId, SessionCaisseAgence.Statut statut);

    List<SessionCaisseAgence> findByAgenceIdAndStatut(Long agenceId, SessionCaisseAgence.Statut statut);

    Page<SessionCaisseAgence> findByAgenceIdInOrderByOuverteLeDesc(Collection<Long> agenceIds, Pageable pageable);
}
