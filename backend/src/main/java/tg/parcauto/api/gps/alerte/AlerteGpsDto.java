package tg.parcauto.api.gps.alerte;

import java.math.BigDecimal;
import java.time.Instant;

public final class AlerteGpsDto {

    private AlerteGpsDto() {
    }

    public record Reponse(
            Long id,
            Long camionId,
            String camionImmatriculation,
            Long zoneId,
            String zoneNom,
            TypeAlerteGps type,
            String typeLibelle,
            GraviteAlerte gravite,
            String graviteLibelle,
            String message,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal vitesseKmh,
            Instant horodatage,
            boolean traitee,
            String traiteePar,
            Instant traiteeLe) {

        public static Reponse de(AlerteGps a) {
            return new Reponse(
                    a.getId(), a.getCamion().getId(), a.getCamion().getImmatriculation(),
                    a.getZone() == null ? null : a.getZone().getId(),
                    a.getZone() == null ? null : a.getZone().getNom(),
                    a.getType(), a.getType().getLibelle(),
                    a.getGravite(), a.getGravite().getLibelle(),
                    a.getMessage(), a.getLatitude(), a.getLongitude(), a.getVitesseKmh(),
                    a.getHorodatage(), a.isTraitee(), a.getTraiteePar(), a.getTraiteeLe());
        }
    }

    /** Compteurs affiches sur le tableau de bord. */
    public record Synthese(
            long nonTraitees,
            long excesVitesse,
            long sortiesDeZone,
            long zonesInterdites,
            long gpsDeconnectes) {
    }
}
