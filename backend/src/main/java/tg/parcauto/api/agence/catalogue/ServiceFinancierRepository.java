package tg.parcauto.api.agence.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceFinancierRepository extends JpaRepository<ServiceFinancier, Long> {

    List<ServiceFinancier> findAllByOrderByOrdreAscNomAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);
}
