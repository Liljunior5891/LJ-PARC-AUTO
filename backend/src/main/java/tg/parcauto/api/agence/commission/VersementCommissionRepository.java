package tg.parcauto.api.agence.commission;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface VersementCommissionRepository extends JpaRepository<VersementCommission, Long> {

    Page<VersementCommission> findByAgenceIdInOrderByDateVersementDescIdDesc(Collection<Long> agences, Pageable pageable);

    Page<VersementCommission> findByAgenceIdInAndServiceIdOrderByDateVersementDescIdDesc(Collection<Long> agences, Long serviceId,
                                                                                        Pageable pageable);
}
