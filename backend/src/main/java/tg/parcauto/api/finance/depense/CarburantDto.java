package tg.parcauto.api.finance.depense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CarburantDto {

    private CarburantDto() {
    }

    /** Un plein, avec la consommation constatee depuis le plein precedent. */
    public record Plein(
            Long depenseId,
            String numero,
            LocalDate date,
            String station,
            Long kilometrage,
            Long distanceDepuisPleinPrecedent,
            BigDecimal litres,
            BigDecimal prixLitre,
            BigDecimal montant,
            BigDecimal consommationL100km,
            String chauffeurNom) {
    }

    /** Synthese carburant d'un camion sur une periode (chapitre 18). */
    public record Synthese(
            Long camionId,
            String camionImmatriculation,
            LocalDate debut,
            LocalDate fin,
            int nombrePleins,
            BigDecimal totalLitres,
            BigDecimal totalMontant,
            long distanceCouverte,
            BigDecimal consommationMoyenneL100km,
            BigDecimal coutCarburantParKm,
            BigDecimal prixMoyenLitre,
            List<Plein> pleins) {
    }
}
