package tg.parcauto.api.pointdevente.associe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AssociePartRepository extends JpaRepository<AssociePart, Long> {

    @Query("select p from AssociePart p left join fetch p.article left join fetch p.categorie order by p.associeId, p.id")
    List<AssociePart> toutes();

    List<AssociePart> findByAssocieId(Long associeId);
}
