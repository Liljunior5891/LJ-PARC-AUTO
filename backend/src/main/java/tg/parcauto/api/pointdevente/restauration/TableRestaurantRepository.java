package tg.parcauto.api.pointdevente.restauration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TableRestaurantRepository extends JpaRepository<TableRestaurant, Long> {

    @Query("select t from TableRestaurant t join fetch t.salle order by t.nom")
    List<TableRestaurant> toutes();

    boolean existsByNomIgnoreCase(String nom);

    boolean existsByNomIgnoreCaseAndIdNot(String nom, Long id);
}
