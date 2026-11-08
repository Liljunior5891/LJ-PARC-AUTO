package tg.parcauto.api.agence.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TypeOperationRepository extends JpaRepository<TypeOperation, Long> {

    List<TypeOperation> findAllByOrderByOrdreAscLibelleAsc();

    List<TypeOperation> findByServiceIdAndActifTrue(Long serviceId);

    boolean existsByServiceIdAndCodeIgnoreCase(Long serviceId, String code);
}
