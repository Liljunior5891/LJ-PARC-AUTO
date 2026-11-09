package tg.parcauto.api.presence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LieuPresenceRepository extends JpaRepository<LieuPresence, Long> {

    List<LieuPresence> findAllByOrderByNomAsc();

    boolean existsByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);
}
