package tg.parcauto.api.gps.position;

import tg.parcauto.api.parc.camion.StatutCamion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PositionGpsDto {

    private PositionGpsDto() {
    }

    public record Reponse(
            Long id,
            Long camionId,
            String camionImmatriculation,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal vitesseKmh,
            Integer direction,
            Long kilometrage,
            Boolean moteurAllume,
            Instant horodatage,
            String fournisseur) {

        public static Reponse de(PositionGps p) {
            return new Reponse(
                    p.getId(), p.getCamion().getId(), p.getCamion().getImmatriculation(),
                    p.getLatitude(), p.getLongitude(), p.getVitesseKmh(), p.getDirection(),
                    p.getKilometrage(), p.getMoteurAllume(), p.getHorodatage(), p.getFournisseur());
        }
    }

    /** Marqueur de la carte du parc, chapitre 23. */
    public record MarqueurCarte(
            Long camionId,
            String immatriculation,
            String marque,
            StatutCamion statut,
            String statutLibelle,
            String chauffeurNom,
            String activite,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal vitesseKmh,
            Integer direction,
            Instant derniereCommunication,
            boolean horsConnexion) {
    }

    /** Segment de trajet reconstitue, chapitre 24. */
    public record Trajet(
            Long camionId,
            String immatriculation,
            Instant debut,
            Instant fin,
            int nombrePoints,
            long distanceKm,
            BigDecimal vitesseMoyenne,
            BigDecimal vitesseMaximale,
            long dureeMinutes,
            int nombreArrets,
            List<Reponse> points) {
    }
}
