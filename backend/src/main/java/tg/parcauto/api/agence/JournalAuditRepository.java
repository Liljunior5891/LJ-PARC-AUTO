package tg.parcauto.api.agence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalAuditRepository extends JpaRepository<JournalAudit, Long>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<JournalAudit> {

    Page<JournalAudit> findAllByOrderByDateActionDescIdDesc(Pageable pageable);
}
