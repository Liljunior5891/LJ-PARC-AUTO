package tg.parcauto.api.gps.integration;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Position normalisee, independante du fabricant du traceur.
 * <p>
 * Chaque adaptateur traduit le format propre a son fournisseur vers cette
 * structure : le reste de l'application n'a jamais connaissance des formats
 * constructeurs (chapitre 22).
 *
 * @param deviceId    identifiant du traceur chez le fournisseur
 * @param horodatage  instant de la mesure, jamais celui de la reception
 */
public record PositionBrute(
        String deviceId,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal vitesseKmh,
        Integer direction,
        BigDecimal altitude,
        Long kilometrage,
        Boolean moteurAllume,
        Instant horodatage) {

    public boolean coordonneesValides() {
        return latitude != null && longitude != null
                && latitude.doubleValue() >= -90 && latitude.doubleValue() <= 90
                && longitude.doubleValue() >= -180 && longitude.doubleValue() <= 180;
    }
}
