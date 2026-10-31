package tg.parcauto.api.pointdevente.caisse;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ligne de billettage : combien de billets ou de pieces d'une valeur ont ete
 * comptes dans le tiroir, a l'ouverture ou a la fermeture de la session.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Coupure {

    public enum Moment { OUVERTURE, FERMETURE }

    /** Billets et pieces du franc CFA, de la plus forte a la plus faible. */
    public static final List<BigDecimal> VALEURS = List.of(10000, 5000, 2000, 1000, 500, 250, 200, 100, 50, 25, 10, 5)
            .stream().map(BigDecimal::valueOf).toList();

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

    /** Les valeurs a partir de 1000 sont des billets, les autres des pieces. */
    public static String nature(BigDecimal valeur) {
        return valeur.compareTo(BigDecimal.valueOf(1000)) >= 0 ? "Billet" : "Piece";
    }
}
