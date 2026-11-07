package tg.parcauto.api.pointdevente.depense;

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

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Depense qui revient chaque mois (loyer, salaire, abonnement) : chaque mois
 * entre {@link #debut} et {@link #fin} est propose a payer tant qu'aucune
 * depense valide ne le regle.
 */
@Entity
@Table(name = "pdv_depense_recurrente")
@Getter
@Setter
@NoArgsConstructor
public class DepenseRecurrente extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String libelle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categorie_id", nullable = false)
    private CategorieDepensePdv categorie;

    @Column(length = 150)
    private String beneficiaire;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal montant;

    /** Jour du mois ou la depense est due (1 a 28). */
    @Column(name = "jour_echeance", nullable = false)
    private int jourEcheance = 1;

    /** Moyen de paiement propose par defaut. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ModeSortie mode;

    /** Premier mois du (premier jour du mois). */
    @Column(nullable = false)
    private LocalDate debut;

    /** Dernier mois du, vide si sans fin. */
    @Column
    private LocalDate fin;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(length = 255)
    private String note;
}
