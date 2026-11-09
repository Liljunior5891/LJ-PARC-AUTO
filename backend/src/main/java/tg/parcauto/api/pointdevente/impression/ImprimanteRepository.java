package tg.parcauto.api.pointdevente.impression;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImprimanteRepository extends JpaRepository<Imprimante, Long> {

    List<Imprimante> findAllByOrderByNomAsc();

    boolean existsByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);
}
