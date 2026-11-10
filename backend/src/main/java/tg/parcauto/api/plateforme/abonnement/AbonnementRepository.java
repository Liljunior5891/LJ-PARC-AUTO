package tg.parcauto.api.plateforme.abonnement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AbonnementRepository extends JpaRepository<Abonnement, Long> {

    Optional<Abonnement> findByEntrepriseId(Long entrepriseId);

    long countByFormuleId(Long formuleId);
}
