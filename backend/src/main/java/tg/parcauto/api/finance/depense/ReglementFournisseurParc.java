package tg.parcauto.api.finance.depense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;
import tg.parcauto.api.finance.commun.ModePaiement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Reglement (partiel ou total) d'une depense a payer : il diminue la dette envers le fournisseur. */
@Entity
@Table(name = "parc_reglement_fournisseur")
@Getter
@Setter
@NoArgsConstructor
public class ReglementFournisseurParc extends BaseEntity {

    public enum Statut { VALIDE, ANNULE }

    @Column(nullable = false, length = 40)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "depense_id", nullable = false)
    private Depense depense;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal montant;

    @Column(name = "date_reglement", nullable = false)
    private LocalDate dateReglement;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_paiement", length = 25)
    private ModePaiement modePaiement;

    @Column(length = 80)
    private String reference;

    @Column(length = 255)
    private String note;

    @Column(name = "auteur_nom", nullable = false, length = 150)
    private String auteurNom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.VALIDE;

    @Column(name = "annule_le")
    private Instant annuleLe;

    @Column(name = "annule_par", length = 150)
    private String annulePar;

    @Column(name = "motif_annulation", length = 255)
    private String motifAnnulation;
}
