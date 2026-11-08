package tg.parcauto.api.agence.tresorerie;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CompteAgenceRepository extends JpaRepository<CompteAgence, Long> {

    List<CompteAgence> findByAgenceIdOrderByIdAsc(Long agenceId);

    List<CompteAgence> findByAgenceIdIn(Collection<Long> agenceIds);
}
