package tg.parcauto.api.finance.depense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DepenseRecurrenteParcRepository extends JpaRepository<DepenseRecurrenteParc, Long> {

    @Query("""
            select r from DepenseRecurrenteParc r left join fetch r.camion left join fetch r.categorieCharge
            left join fetch r.fournisseur order by r.libelle
            """)
    List<DepenseRecurrenteParc> toutes();
}
