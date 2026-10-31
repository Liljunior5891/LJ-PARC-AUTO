package tg.parcauto.api.pointdevente.caisse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaisseRepository extends JpaRepository<Caisse, Long> {

    List<Caisse> findAllByOrderByNomAsc();

    boolean existsByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);
}
