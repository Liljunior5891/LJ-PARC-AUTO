package tg.parcauto.api.agence.horsligne;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OperationHorsLigneRepository extends JpaRepository<OperationHorsLigne, Long> {

    Optional<OperationHorsLigne> findByCle(String cle);

    List<OperationHorsLigne> findByStatutAndAgenceIdInOrderBySaisieLeDesc(OperationHorsLigne.Statut statut, Collection<Long> agences);

    long countByStatutAndAgenceIdIn(OperationHorsLigne.Statut statut, Collection<Long> agences);
}
