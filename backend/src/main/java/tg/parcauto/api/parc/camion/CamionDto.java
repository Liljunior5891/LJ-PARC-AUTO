package tg.parcauto.api.parc.camion;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class CamionDto {

    private CamionDto() {
    }

    public record Requete(
            @NotBlank @Size(max = 40) String immatriculation,
            Long marqueId,
            @Size(max = 60) String marque,
            @Size(max = 60) String modele,
            @Min(1950) Integer annee,
            Long typeId,
            @Size(max = 60) String type,
            @Size(max = 60) String categorie,
            @Size(max = 60) String numeroChassis,
            @Size(max = 60) String numeroMoteur,
            Long couleurId,
            @Size(max = 40) String couleur,
            @PositiveOrZero BigDecimal capaciteTonnes,
            TypeCarburant typeCarburant,
            LocalDate dateAcquisition,
            @PositiveOrZero BigDecimal prixAchat,
            @PositiveOrZero Long kilometrageInitial,
            @PositiveOrZero Long kilometrageActuel,
            StatutCamion statut,
            @Size(max = 500) String photoUrl,
            @Size(max = 80) String gpsDeviceId,
            @Min(30) Integer vitesseMaxKmh,
            String observations,
            Boolean actif) {
    }

    public record Reponse(
            Long id,
            String immatriculation,
            Long marqueId,
            String marque,
            String marqueLogoUrl,
            String marqueCouleur,
            String marqueMonogramme,
            String modele,
            Integer annee,
            Long typeId,
            String type,
            String categorie,
            String numeroChassis,
            String numeroMoteur,
            Long couleurId,
            String couleur,
            String couleurCodeHexa,
            BigDecimal capaciteTonnes,
            TypeCarburant typeCarburant,
            LocalDate dateAcquisition,
            BigDecimal prixAchat,
            Long kilometrageInitial,
            Long kilometrageActuel,
            long kilometrageParcouru,
            StatutCamion statut,
            String statutLibelle,
            String photoUrl,
            String gpsDeviceId,
            Integer vitesseMaxKmh,
            String observations,
            boolean actif) {

        public static Reponse de(Camion c) {
            return new Reponse(
                    c.getId(), c.getImmatriculation(),
                    c.getMarqueReference() == null ? null : c.getMarqueReference().getId(),
                    c.libelleMarque(),
                    c.getMarqueReference() == null ? null : c.getMarqueReference().getLogoUrl(),
                    c.getMarqueReference() == null ? null : c.getMarqueReference().getCouleur(),
                    c.getMarqueReference() == null ? null : c.getMarqueReference().monogramme(),
                    c.getModele(),
                    c.getAnnee(),
                    c.getTypeReference() == null ? null : c.getTypeReference().getId(),
                    c.libelleType(), c.getCategorie(), c.getNumeroChassis(), c.getNumeroMoteur(),
                    c.getCouleurReference() == null ? null : c.getCouleurReference().getId(),
                    c.libelleCouleur(),
                    c.getCouleurReference() == null ? null : c.getCouleurReference().getCodeHexa(),
                    c.getCapaciteTonnes(), c.getTypeCarburant(), c.getDateAcquisition(),
                    c.getPrixAchat(), c.getKilometrageInitial(), c.getKilometrageActuel(), c.kilometrageParcouru(),
                    c.getStatut(), c.getStatut().getLibelle(), c.getPhotoUrl(), c.getGpsDeviceId(),
                    c.getVitesseMaxKmh(), c.getObservations(), c.isActif());
        }
    }

    /** Mise a jour du compteur kilometrique (saisie terrain ou application mobile). */
    public record MajKilometrage(@PositiveOrZero long kilometrage) {
    }

    public record MajStatut(StatutCamion statut, String motif) {
    }
}
