package tg.parcauto.api.pointdevente.depense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategorieDepensePdvRepository extends JpaRepository<CategorieDepensePdv, Long> {

    List<CategorieDepensePdv> findAllByOrderByOrdreAscNomAsc();

    @Query("select c from CategorieDepensePdv c where lower(trim(c.nom)) = lower(trim(:nom))")
    List<CategorieDepensePdv> parNom(@Param("nom") String nom);
}
