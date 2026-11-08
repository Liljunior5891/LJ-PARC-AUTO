package tg.parcauto.api.agence.mouvement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MouvementAgenceRepository extends JpaRepository<MouvementAgence, Long>,
        JpaSpecificationExecutor<MouvementAgence> {
}
