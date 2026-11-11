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

    public static final long KILOMETRAGE_MAX = 10_000_000L;

    /** Valeurs dans des bornes physiques : sinon la position seule est ecartee, pas tout le lot. */
    public boolean valeursPlausibles() {
        return (altitude == null || (altitude.doubleValue() >= -500 && altitude.doubleValue() <= 9000))
                && (vitesseKmh == null || (vitesseKmh.doubleValue() >= -1 && vitesseKmh.doubleValue() <= 1000))
                && (kilometrage == null || (kilometrage >= 0 && kilometrage <= KILOMETRAGE_MAX));
    }

    public boolean coordonneesValides() {
        return latitude != null && longitude != null
                && latitude.doubleValue() >= -90 && latitude.doubleValue() <= 90
                && longitude.doubleValue() >= -180 && longitude.doubleValue() <= 180;
    }
}
