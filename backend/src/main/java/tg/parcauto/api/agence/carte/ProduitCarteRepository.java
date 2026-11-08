package tg.parcauto.api.agence.carte;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProduitCarteRepository extends JpaRepository<ProduitCarte, Long> {

    List<ProduitCarte> findAllByOrderByNomAsc();

    boolean existsByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);
}
