package tg.parcauto.api.pointdevente.depense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DepenseRecurrenteRepository extends JpaRepository<DepenseRecurrente, Long> {

    @Query("select r from DepenseRecurrente r join fetch r.categorie order by r.actif desc, lower(r.libelle)")
    List<DepenseRecurrente> toutes();

    @Query("select r from DepenseRecurrente r join fetch r.categorie where r.actif = true order by lower(r.libelle)")
    List<DepenseRecurrente> actives();
}
