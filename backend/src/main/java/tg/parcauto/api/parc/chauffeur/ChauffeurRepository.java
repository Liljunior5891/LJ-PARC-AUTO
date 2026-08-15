package tg.parcauto.api.parc.chauffeur;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ChauffeurRepository extends JpaRepository<Chauffeur, Long> {

    boolean existsByMatriculeIgnoreCase(String matricule);

    long countByStatut(StatutChauffeur statut);

    @Query("""
            select c from Chauffeur c
            where (:statut is null or c.statut = :statut)
              and (:actif is null or c.actif = :actif)
              and (:q is null or lower(c.nom) like lower(concat('%', :q, '%'))
                   or lower(c.prenom) like lower(concat('%', :q, '%'))
                   or lower(c.matricule) like lower(concat('%', :q, '%'))
                   or c.telephone like concat('%', :q, '%'))
            """)
    Page<Chauffeur> rechercher(@Param("q") String q,
                               @Param("statut") StatutChauffeur statut,
                               @Param("actif") Boolean actif,
                               Pageable pageable);

    @Query("""
            select c from Chauffeur c
            where c.actif = true and c.permisExpiration is not null and c.permisExpiration <= :limite
            order by c.permisExpiration asc
            """)
    List<Chauffeur> permisExpirantAvant(@Param("limite") LocalDate limite);
}
