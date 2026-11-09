package tg.parcauto.api.finance.depense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FournisseurParcRepository extends JpaRepository<FournisseurParc, Long> {

    List<FournisseurParc> findAllByOrderByNomAsc();

    Optional<FournisseurParc> findFirstByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);
}
