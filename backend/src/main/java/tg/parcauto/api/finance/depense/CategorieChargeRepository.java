package tg.parcauto.api.finance.depense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategorieChargeRepository extends JpaRepository<CategorieCharge, Long> {

    List<CategorieCharge> findAllByOrderByOrdreAscNomAsc();

    boolean existsByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);
}
