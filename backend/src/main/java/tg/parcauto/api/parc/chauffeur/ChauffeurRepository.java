package tg.parcauto.api.parc.chauffeur;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ChauffeurRepository extends JpaRepository<Chauffeur, Long>,
        JpaSpecificationExecutor<Chauffeur> {

    boolean existsByMatriculeIgnoreCase(String matricule);

    long countByStatut(StatutChauffeur statut);

    @Query("""
            select c from Chauffeur c
            where c.actif = true and c.permisExpiration is not null and c.permisExpiration <= :limite
            order by c.permisExpiration asc
            """)
    List<Chauffeur> permisExpirantAvant(@Param("limite") LocalDate limite);
}
