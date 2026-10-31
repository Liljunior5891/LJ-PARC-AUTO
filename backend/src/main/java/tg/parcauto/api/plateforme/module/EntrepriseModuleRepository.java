package tg.parcauto.api.plateforme.module;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EntrepriseModuleRepository extends JpaRepository<EntrepriseModule, Long> {

    List<EntrepriseModule> findByEntrepriseIdAndActifTrue(Long entrepriseId);

    List<EntrepriseModule> findByEntrepriseId(Long entrepriseId);

    Optional<EntrepriseModule> findByEntrepriseIdAndModule(Long entrepriseId, ModuleApplicatif module);

    boolean existsByEntrepriseIdAndModuleAndActifTrue(Long entrepriseId, ModuleApplicatif module);
}
