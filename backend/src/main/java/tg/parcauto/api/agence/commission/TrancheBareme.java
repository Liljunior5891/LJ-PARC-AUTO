package tg.parcauto.api.agence.commission;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Tranche de montant d'un bareme : de montantMin a montantMax inclus, un fixe ou un pourcentage. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrancheBareme {

    @Column(name = "montant_min", nullable = false)
    private long montantMin;

    /** Nul : sans limite haute. */
    @Column(name = "montant_max")
    private Long montantMax;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Bareme.Mode mode;

    @Column(name = "valeur_fixe")
    private Long valeurFixe;

    @Column(precision = 9, scale = 4)
    private BigDecimal taux;

    public boolean contient(long montant) {
        return montant >= montantMin && (montantMax == null || montant <= montantMax);
    }
}
