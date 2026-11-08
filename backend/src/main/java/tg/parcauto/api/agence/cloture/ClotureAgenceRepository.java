package tg.parcauto.api.agence.cloture;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClotureAgenceRepository extends JpaRepository<ClotureAgence, Long> {

    Optional<ClotureAgence> findByAgenceIdAndJournee(Long agenceId, LocalDate journee);

    boolean existsByAgenceIdAndJourneeAndStatut(Long agenceId, LocalDate journee, ClotureAgence.Statut statut);

    Page<ClotureAgence> findByAgenceIdInOrderByJourneeDescIdDesc(Collection<Long> agenceIds, Pageable pageable);

    List<ClotureAgence> findByAgenceIdInAndJourneeGreaterThanEqual(Collection<Long> agenceIds, LocalDate depuis);
}
