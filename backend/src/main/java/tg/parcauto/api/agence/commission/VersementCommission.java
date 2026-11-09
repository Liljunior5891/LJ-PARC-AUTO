package tg.parcauto.api.agence.commission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Versement de commissions par un operateur : montant recu sur un compte de
 * l'agence, commissions qu'il couvre, et ecart entre attendu et verse.
 */
@Entity
@Table(name = "agc_versement_commission")
@Getter
@Setter
@NoArgsConstructor
public class VersementCommission extends BaseEntity {

    public enum Statut { VALIDE, ANNULE }

    @Column(name = "agence_id", nullable = false, updatable = false)
    private Long agenceId;

    @Column(name = "service_id", nullable = false, updatable = false)
    private Long serviceId;

    @Column(nullable = false, length = 40, updatable = false)
    private String reference;

    @Column(name = "reference_operateur", length = 80, updatable = false)
    private String referenceOperateur;

    @Column(name = "date_versement", nullable = false, updatable = false)
    private Instant dateVersement;

    @Column(nullable = false, updatable = false)
    private LocalDate journee;

    @Column(name = "montant_attendu", nullable = false, updatable = false)
    private long montantAttendu;

    @Column(name = "montant_verse", nullable = false, updatable = false)
    private long montantVerse;

    /** Verse - attendu : negatif, l'operateur a verse moins que prevu. */
    @Column(nullable = false, updatable = false)
    private long ecart;

    @Column(name = "nombre_commissions", nullable = false, updatable = false)
    private int nombreCommissions;

    @Column(name = "compte_id", nullable = false, updatable = false)
    private Long compteId;

    @Column(length = 500, updatable = false)
    private String observations;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.VALIDE;

    @Column(name = "auteur_nom", length = 150, updatable = false)
    private String auteurNom;

    @Column(name = "ecriture_id")
    private Long ecritureId;

    @Column(name = "annulation_ecriture_id")
    private Long annulationEcritureId;

    @Column(name = "motif_annulation", length = 500)
    private String motifAnnulation;
}
