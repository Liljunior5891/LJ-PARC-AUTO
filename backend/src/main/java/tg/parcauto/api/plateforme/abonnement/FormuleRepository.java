package tg.parcauto.api.plateforme.abonnement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormuleRepository extends JpaRepository<Formule, Long> {

    List<Formule> findAllByOrderByOrdreAscNomAsc();

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);

    boolean existsByNomIgnoreCase(String nom);
}
