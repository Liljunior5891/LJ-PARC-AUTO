package tg.parcauto.api.presence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BornePresenceRepository extends JpaRepository<BornePresence, Long> {

    List<BornePresence> findAllByOrderByNomAsc();

    Optional<BornePresence> findByJetonEmpreinte(String empreinte);
}
