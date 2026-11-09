package tg.parcauto.api.presence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployePresenceRepository extends JpaRepository<EmployePresence, Long> {

    List<EmployePresence> findAllByOrderByNomAscPrenomAsc();

    Optional<EmployePresence> findByPinEmpreinte(String empreinte);

    boolean existsByPinEmpreinte(String empreinte);

    Optional<EmployePresence> findByUtilisateurId(Long utilisateurId);

    boolean existsByUtilisateurId(Long utilisateurId);
}
