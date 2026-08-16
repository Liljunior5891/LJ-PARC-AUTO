package tg.parcauto.api.gps.zone;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class ZoneGeofenceDto {

    private ZoneGeofenceDto() {
    }

    public record Requete(
            @NotBlank @Size(max = 120) String nom,
            @NotNull TypeZone type,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @NotNull @Positive Integer rayonMetres,
            Boolean alerteEntree,
            Boolean alerteSortie,
            @Positive Integer dureeMaxMinutes,
            @Size(max = 20) String couleur,
            String observations,
            Boolean actif) {
    }

    public record Reponse(
            Long id,
            String nom,
            TypeZone type,
            String typeLibelle,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer rayonMetres,
            boolean alerteEntree,
            boolean alerteSortie,
            Integer dureeMaxMinutes,
            String couleur,
            String observations,
            boolean actif) {

        public static Reponse de(ZoneGeofence z) {
            return new Reponse(
                    z.getId(), z.getNom(), z.getType(), z.getType().getLibelle(),
                    z.getLatitude(), z.getLongitude(), z.getRayonMetres(),
                    z.isAlerteEntree(), z.isAlerteSortie(), z.getDureeMaxMinutes(),
                    z.getCouleur() == null ? z.getType().getCouleurParDefaut() : z.getCouleur(),
                    z.getObservations(), z.isActif());
        }
    }
}
