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

    /** Paiement PayGate verrouille : rappel et verification periodique ne le traitent pas deux fois. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "select p from PaiementAbonnement p where p.paygateIdentifiant = :identifiant")
    java.util.Optional<PaiementAbonnement> verrouillerPayGate(
            @org.springframework.data.repository.query.Param("identifiant") String identifiant);

    /** Paiements PayGate en attente, a verifier aupres de PayGate. */
    List<PaiementAbonnement> findByMoyenAndStatutAndCreeLeAfter(MoyenPaiementAbonnement moyen,
                                                                StatutPaiementAbonnement statut,
                                                                java.time.Instant depuis);
}
