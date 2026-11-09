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

    Optional<SessionCaisseAgence> findFirstByCaissierIdAndStatutIn(Long caissierId, Collection<SessionCaisseAgence.Statut> statuts);

    List<SessionCaisseAgence> findByAgenceIdAndStatutIn(Long agenceId, Collection<SessionCaisseAgence.Statut> statuts);

    Page<SessionCaisseAgence> findByAgenceIdInOrderByOuverteLeDesc(Collection<Long> agenceIds, Pageable pageable);
}
