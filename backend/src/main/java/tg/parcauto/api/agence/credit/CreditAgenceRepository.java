package tg.parcauto.api.agence.credit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CreditAgenceRepository extends JpaRepository<CreditAgence, Long> {

    List<CreditAgence> findByAgenceIdInOrderByDateDebutDescIdDesc(Collection<Long> agenceIds);
}
