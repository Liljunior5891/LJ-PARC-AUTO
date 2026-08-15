package tg.parcauto.api.finance.facture;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.parc.camion.Camion;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Ligne de facture : une prestation, rattachee au camion qui l'a executee. */
@Entity
@Table(name = "facture_ligne")
@Getter
@Setter
@NoArgsConstructor
public class FactureLigne {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facture_id", nullable = false)
    private Facture facture;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camion_id")
    private Camion camion;

    @Column(nullable = false, length = 255)
    private String designation;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal quantite = BigDecimal.ONE;

    @Column(length = 30)
    private String unite;

    @Column(name = "prix_unitaire", nullable = false, precision = 16, scale = 2)
    private BigDecimal prixUnitaire = BigDecimal.ZERO;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal montant = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer ordre = 0;

    /** Montant de la ligne, recalcule a partir de la quantite et du prix unitaire. */
    public BigDecimal montantCalcule() {
        BigDecimal calcule = quantite.multiply(prixUnitaire).setScale(2, RoundingMode.HALF_UP);
        this.montant = calcule;
        return calcule;
    }
}
