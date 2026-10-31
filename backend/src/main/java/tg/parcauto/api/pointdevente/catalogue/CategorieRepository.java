package tg.parcauto.api.pointdevente.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategorieRepository extends JpaRepository<Categorie, Long> {

    List<Categorie> findAllByOrderByOrdreAscNomAsc();

    boolean existsByNomIgnoreCase(String nom);
}
