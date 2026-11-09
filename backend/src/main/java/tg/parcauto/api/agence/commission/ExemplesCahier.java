package tg.parcauto.api.agence.commission;

import java.math.BigDecimal;
import java.util.List;

/**
 * Exemples de commissions du cahier des charges (6.3) et baremes d'exemple qui
 * les reproduisent. Ce ne sont pas des baremes definitifs : ils servent a
 * verifier le moteur et a demarrer, avant la saisie des baremes reels.
 */
final class ExemplesCahier {

    /** Operation donnee en exemple : service, type, reseau (codes), montant et commission constatee. */
    record Cas(String service, String type, String reseau, long montant, long attendue) {
    }

    record Modele(String service, String type, String reseau, String libelle, Bareme.Mode mode, BigDecimal taux,
                  List<TrancheBareme> tranches) {
    }

    static final List<Cas> CAS = List.of(
            new Cas("KEAZ", "DEPOT", null, 100_000, 250),
            new Cas("KEAZ", "DEPOT", null, 900_000, 2_250),
            new Cas("KEAZ", "RETRAIT", null, 190_000, 380),
            new Cas("KEAZ", "RETRAIT", null, 1_800_000, 9_000),
            new Cas("RAPID_TRANSFER", null, "MONEYGRAM", 1_000_000, 2_478),
            new Cas("RAPID_TRANSFER", null, "WESTERN_UNION", 57_000, 298),
            new Cas("RAPID_TRANSFER", null, "KORI", 113_602, 271),
            // Memes exemples une fois les reseaux devenus des services a part.
            new Cas("MONEYGRAM", null, null, 1_000_000, 2_478),
            new Cas("WESTERN_UNION", null, null, 57_000, 298),
            new Cas("KORI", null, null, 113_602, 271));

    static final List<Modele> MODELES = List.of(
            new Modele("KEAZ", "DEPOT", null, "Keaz Spot - depot (exemple du cahier)", Bareme.Mode.POURCENTAGE,
                    new BigDecimal("0.25"), List.of()),
            // Le retrait n'est pas lineaire : 0,2 % jusqu'a 1 000 000 F, 0,5 % au-dela (seuil a confirmer).
            new Modele("KEAZ", "RETRAIT", null, "Keaz Spot - retrait (exemple du cahier)", Bareme.Mode.TRANCHES, null,
                    List.of(new TrancheBareme(0, 999_999L, Bareme.Mode.POURCENTAGE, null, new BigDecimal("0.2")),
                            new TrancheBareme(1_000_000, null, Bareme.Mode.POURCENTAGE, null, new BigDecimal("0.5")))),
            new Modele("RAPID_TRANSFER", null, "MONEYGRAM", "MoneyGram (exemple du cahier)", Bareme.Mode.POURCENTAGE,
                    new BigDecimal("0.2478"), List.of()),
            new Modele("RAPID_TRANSFER", null, "WESTERN_UNION", "Western Union (exemple du cahier)", Bareme.Mode.POURCENTAGE,
                    new BigDecimal("0.5228"), List.of()),
            new Modele("RAPID_TRANSFER", null, "KORI", "KORI (exemple du cahier)", Bareme.Mode.POURCENTAGE,
                    new BigDecimal("0.2386"), List.of()),
            new Modele("MONEYGRAM", null, null, "MoneyGram (exemple du cahier)", Bareme.Mode.POURCENTAGE,
                    new BigDecimal("0.2478"), List.of()),
            new Modele("WESTERN_UNION", null, null, "Western Union (exemple du cahier)", Bareme.Mode.POURCENTAGE,
                    new BigDecimal("0.5228"), List.of()),
            new Modele("KORI", null, null, "KORI (exemple du cahier)", Bareme.Mode.POURCENTAGE,
                    new BigDecimal("0.2386"), List.of()));

    private ExemplesCahier() {
    }
}
