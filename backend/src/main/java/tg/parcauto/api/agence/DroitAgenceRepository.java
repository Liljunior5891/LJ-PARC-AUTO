package tg.parcauto.api.agence;

import org.springframework.data.jpa.repository.JpaRepository;
import tg.parcauto.api.security.Role;

import java.util.Collection;
import java.util.List;

public interface DroitAgenceRepository extends JpaRepository<DroitAgence, Long> {

    List<DroitAgence> findByRoleIn(Collection<Role> roles);

    long count();
}
