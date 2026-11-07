package tg.parcauto.api.pointdevente.tresorerie;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Ligne de billettage d'une remise ou d'un degagement : celle declaree par qui
 * envoie l'argent, puis celle recomptee par qui le recoit.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CoupureTresorerie {

    public enum Moment { DECLARE, VERIFIE }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Moment moment;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valeur;

    @Column(nullable = false)
    private int quantite;

    public BigDecimal montant() {
        return valeur.multiply(BigDecimal.valueOf(quantite));
    }
}
