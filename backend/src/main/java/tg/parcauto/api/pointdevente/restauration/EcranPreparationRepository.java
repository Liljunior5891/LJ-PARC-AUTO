package tg.parcauto.api.pointdevente.restauration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EcranPreparationRepository extends JpaRepository<EcranPreparation, Long> {

    List<EcranPreparation> findAllByOrderByNomAsc();

    boolean existsByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);
}
