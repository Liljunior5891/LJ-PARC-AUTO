package tg.parcauto.api.maintenance.entretien;

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
import tg.parcauto.api.finance.depense.Depense;
import tg.parcauto.api.parc.camion.Camion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Intervention de maintenance, chapitre 19 du cahier des charges.
 * <p>
 * L'echeance peut etre kilometrique, calendaire ou les deux : la premiere
 * atteinte declenche l'alerte, comme dans l'exemple du cahier des charges
 * (derniere vidange a 120 000 km, prochaine a 130 000 km).
 */
@Entity
@Table(name = "entretien")
@Getter
@Setter
@NoArgsConstructor
public class Entretien extends BaseEntity {

    /** Marge d'anticipation kilometrique de l'alerte. */
    private static final long SEUIL_ALERTE_KM = 1_000L;
    /** Marge d'anticipation calendaire de l'alerte, en jours. */
    private static final int SEUIL_ALERTE_JOURS = 15;

    @Column(nullable = false, unique = true, length = 40)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "camion_id", nullable = false)
    private Camion camion;

    /** Depense generee lors de la realisation, pour alimenter la rentabilite. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depense_id")
    private Depense depense;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TypeEntretien type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutEntretien statut = StatutEntretien.PLANIFIE;

    @Column(nullable = false, length = 255)
    private String libelle;

    @Column(name = "date_prevue")
    private LocalDate datePrevue;

    @Column(name = "date_realisation")
    private LocalDate dateRealisation;

    @Column(name = "kilometrage_prevu")
    private Long kilometragePrevu;

    @Column(name = "kilometrage_realise")
    private Long kilometrageRealise;

    @Column(name = "periodicite_km")
    private Long periodiciteKm;

    @Column(name = "periodicite_jours")
    private Integer periodiciteJours;

    @Column(precision = 16, scale = 2)
    private BigDecimal cout;

    @Column(length = 180)
    private String fournisseur;

    @Column(length = 180)
    private String intervenant;

    @Column(name = "pieces_remplacees", columnDefinition = "text")
    private String piecesRemplacees;

    @Column(name = "immobilisation_jours")
    private Integer immobilisationJours;

    @Column(columnDefinition = "text")
    private String observations;

    /** Kilometres restants avant l'echeance ; negatif si elle est depassee. */
    public Long kilometresRestants() {
        if (kilometragePrevu == null) {
            return null;
        }
        return kilometragePrevu - camion.getKilometrageActuel();
    }

    /** Jours restants avant l'echeance ; negatif si elle est depassee. */
    public Long joursRestants() {
        if (datePrevue == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), datePrevue);
    }

    /** Vrai si l'echeance est atteinte ou proche, sur l'un des deux criteres. */
    public boolean echeanceProche() {
        if (statut.clos()) {
            return false;
        }
        Long km = kilometresRestants();
        Long jours = joursRestants();
        return (km != null && km <= SEUIL_ALERTE_KM) || (jours != null && jours <= SEUIL_ALERTE_JOURS);
    }

    /** Vrai si l'echeance est franchement depassee. */
    public boolean enRetard() {
        if (statut.clos()) {
            return false;
        }
        Long km = kilometresRestants();
        Long jours = joursRestants();
        return (km != null && km < 0) || (jours != null && jours < 0);
    }

    /** Kilometrage de la prochaine occurrence, apres realisation. */
    public Long prochainKilometrage() {
        if (periodiciteKm == null || kilometrageRealise == null) {
            return null;
        }
        return kilometrageRealise + periodiciteKm;
    }

    /** Date de la prochaine occurrence, apres realisation. */
    public LocalDate prochaineDate() {
        if (periodiciteJours == null || dateRealisation == null) {
            return null;
        }
        return dateRealisation.plusDays(periodiciteJours);
    }
}
