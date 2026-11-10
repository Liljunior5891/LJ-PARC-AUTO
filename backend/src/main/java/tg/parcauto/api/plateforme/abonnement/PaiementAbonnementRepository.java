package tg.parcauto.api.plateforme.abonnement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaiementAbonnementRepository extends JpaRepository<PaiementAbonnement, Long> {

    List<PaiementAbonnement> findByEntrepriseIdOrderByCreeLeDesc(Long entrepriseId);

    List<PaiementAbonnement> findAllByOrderByCreeLeDesc();

    List<PaiementAbonnement> findByStatutAndPeriodeDebutLessThanEqualOrderByPeriodeDebutDescIdDesc(
            StatutPaiementAbonnement statut, java.time.LocalDate jour);

    boolean existsByEntrepriseIdAndStatut(Long entrepriseId, StatutPaiementAbonnement statut);

    long countByFormuleId(Long formuleId);
}
