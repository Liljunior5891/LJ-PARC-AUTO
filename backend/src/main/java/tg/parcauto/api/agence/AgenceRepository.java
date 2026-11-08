package tg.parcauto.api.agence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgenceRepository extends JpaRepository<Agence, Long> {

    List<Agence> findAllByOrderByNomAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    boolean existsByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);
}
