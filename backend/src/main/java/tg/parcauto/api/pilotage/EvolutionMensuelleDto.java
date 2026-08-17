package tg.parcauto.api.pilotage;

import java.math.BigDecimal;

/** Recettes, depenses et resultat d'un mois. */
public record EvolutionMensuelleDto(
        String mois,
        String moisLibelle,
        int annee,
        BigDecimal recettes,
        BigDecimal depenses,
        BigDecimal resultat) {
}
