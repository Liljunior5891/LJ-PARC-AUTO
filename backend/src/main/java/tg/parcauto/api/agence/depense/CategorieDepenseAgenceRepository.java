package tg.parcauto.api.agence.depense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategorieDepenseAgenceRepository extends JpaRepository<CategorieDepenseAgence, Long> {

    List<CategorieDepenseAgence> findAllByOrderByOrdreAscNomAsc();

    boolean existsByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);
}
