package tg.parcauto.api.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UtilisateurRepository
        extends JpaRepository<Utilisateur, Long>, JpaSpecificationExecutor<Utilisateur> {

    Optional<Utilisateur> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<Utilisateur> findByIdAndEntrepriseId(Long id, Long entrepriseId);

    long countByEntrepriseId(Long entrepriseId);

    java.util.List<Utilisateur> findByEntrepriseIdOrderByNomAscPrenomAsc(Long entrepriseId);

    @Query("select count(u) > 0 from Utilisateur u join u.roles r where r = :role")
    boolean existeAvecRole(@Param("role") Role role);

    /** Administrateurs actifs de l'entreprise autres que celui qu'on s'apprete a modifier. */
    @Query("""
            select count(u) from Utilisateur u
            join u.roles r
            where r = tg.parcauto.api.security.Role.ADMIN
              and u.actif = true
              and u.id <> :id
              and u.entrepriseId = :entrepriseId
            """)
    long compterAdministrateursActifsHors(@Param("id") Long id, @Param("entrepriseId") Long entrepriseId);
}
