package tg.parcauto.api.agence.commission;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Bareme de commission d'un service, eventuellement limite a un type
 * d'operation et/ou a un reseau, valable sur une periode.
 */
@Entity
@Table(name = "agc_bareme")
@Getter
@Setter
@NoArgsConstructor
public class Bareme extends BaseEntity {

    public enum Mode { FIXE, POURCENTAGE, TRANCHES }

    /** Compte sur lequel la commission est creditee. */
    public enum CompteCredit { COMMISSIONS, FLOAT }

    @Column(name = "service_id", nullable = false, updatable = false)
    private Long serviceId;

    @Column(name = "type_operation_id", updatable = false)
    private Long typeOperationId;

    @Column(name = "reseau_id", updatable = false)
    private Long reseauId;

    @Column(nullable = false, length = 120)
    private String libelle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Mode mode;

    @Column(name = "valeur_fixe")
    private Long valeurFixe;

    /** Pourcentage du montant (0,25 pour 0,25 %). */
    @Column(precision = 9, scale = 4)
    private BigDecimal taux;

    /** Plancher et plafond d'une commission au pourcentage. */
    private Long minimum;

    private Long maximum;

    @Enumerated(EnumType.STRING)
    @Column(name = "compte_credit", nullable = false, length = 20)
    private CompteCredit compteCredit = CompteCredit.COMMISSIONS;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(name = "precedent_id", updatable = false)
    private Long precedentId;

    @Column(nullable = false)
    private boolean exemple;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "agc_bareme_tranche", joinColumns = @JoinColumn(name = "bareme_id"))
    @OrderBy("montantMin")
    private List<TrancheBareme> tranches = new ArrayList<>();

    /** Le bareme s'applique-t-il ce jour-la ? */
    public boolean valableLe(LocalDate jour) {
        return actif && !jour.isBefore(dateDebut) && (dateFin == null || !jour.isAfter(dateFin));
    }
}
