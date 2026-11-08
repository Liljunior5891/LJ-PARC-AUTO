package tg.parcauto.api.agence.credit;

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

/** Remboursement d'un credit : capital (pas une charge) et interets ou frais (une charge). */
@Entity
@Table(name = "agc_remboursement")
@Getter
@Setter
@NoArgsConstructor
public class RemboursementAgence extends BaseEntity {

    public enum Statut { VALIDE, ANNULE }

    @Column(name = "credit_id", nullable = false, updatable = false)
    private Long creditId;

    @Column(name = "agence_id", nullable = false, updatable = false)
    private Long agenceId;

    @Column(name = "date_remboursement", nullable = false, updatable = false)
    private Instant dateRemboursement;

    @Column(nullable = false, updatable = false)
    private LocalDate journee;

    @Column(nullable = false, updatable = false)
    private long capital;

    @Column(nullable = false, updatable = false)
    private long interets;

    @Column(name = "compte_id", nullable = false, updatable = false)
    private Long compteId;

    @Column(length = 500)
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
