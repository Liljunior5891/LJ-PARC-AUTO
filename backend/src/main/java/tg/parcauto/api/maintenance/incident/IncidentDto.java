package tg.parcauto.api.maintenance.incident;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class IncidentDto {

    private IncidentDto() {
    }

    /** Declaration, typiquement depuis l'application mobile du chauffeur. */
    public record Requete(
            @NotNull Long camionId,
            Long chauffeurId,
            Long missionId,
            @NotNull TypeIncident type,
            GraviteIncident gravite,
            @NotBlank String description,
            @Size(max = 255) String lieu,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @Size(max = 500) String photoUrl,
            Instant survenuLe,
            @PositiveOrZero BigDecimal coutEstime,
            Boolean immobilisant) {
    }

    /** Suivi du traitement par le gestionnaire de parc. */
    public record Traitement(
            @NotNull StatutIncident statut,
            String resolution,
            @PositiveOrZero BigDecimal coutEstime,
            /** Ouvre une intervention de maintenance pour reparer l'incident. */
            Boolean ouvrirEntretien) {
    }

    public record Reponse(
            Long id,
            String numero,
            Long camionId,
            String camionImmatriculation,
            Long chauffeurId,
            String chauffeurNom,
            Long missionId,
            String missionNumero,
            Long entretienId,
            String entretienNumero,
            TypeIncident type,
            String typeLibelle,
            GraviteIncident gravite,
            String graviteLibelle,
            StatutIncident statut,
            String statutLibelle,
            String description,
            String lieu,
            BigDecimal latitude,
            BigDecimal longitude,
            String photoUrl,
            Instant survenuLe,
            Instant resoluLe,
            Long delaiResolutionHeures,
            String resolution,
            BigDecimal coutEstime,
            boolean immobilisant,
            boolean ouvert) {

        public static Reponse de(Incident i) {
            return new Reponse(
                    i.getId(), i.getNumero(),
                    i.getCamion().getId(), i.getCamion().getImmatriculation(),
                    i.getChauffeur() == null ? null : i.getChauffeur().getId(),
                    i.getChauffeur() == null ? null : i.getChauffeur().nomComplet(),
                    i.getMission() == null ? null : i.getMission().getId(),
                    i.getMission() == null ? null : i.getMission().getNumero(),
                    i.getEntretien() == null ? null : i.getEntretien().getId(),
                    i.getEntretien() == null ? null : i.getEntretien().getNumero(),
                    i.getType(), i.getType().getLibelle(),
                    i.getGravite(), i.getGravite().getLibelle(),
                    i.getStatut(), i.getStatut().getLibelle(),
                    i.getDescription(), i.getLieu(), i.getLatitude(), i.getLongitude(),
                    i.getPhotoUrl(), i.getSurvenuLe(), i.getResoluLe(),
                    i.delaiResolutionHeures(), i.getResolution(), i.getCoutEstime(),
                    i.isImmobilisant(), i.ouvert());
        }
    }
}
