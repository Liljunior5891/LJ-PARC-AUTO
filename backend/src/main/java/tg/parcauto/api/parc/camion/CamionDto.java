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
            @NotBlank @Size(max = 40) String numeroInterne,
            @NotBlank @Size(max = 40) String immatriculation,
            @Size(max = 60) String marque,
            @Size(max = 60) String modele,
            @Min(1950) Integer annee,
            @Size(max = 60) String type,
            @Size(max = 60) String categorie,
            @Size(max = 60) String numeroChassis,
            @Size(max = 60) String numeroMoteur,
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
            String numeroInterne,
            String immatriculation,
            String marque,
            String modele,
            Integer annee,
            String type,
            String categorie,
            String numeroChassis,
            String numeroMoteur,
            String couleur,
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
                    c.getId(), c.getNumeroInterne(), c.getImmatriculation(), c.getMarque(), c.getModele(),
                    c.getAnnee(), c.getType(), c.getCategorie(), c.getNumeroChassis(), c.getNumeroMoteur(),
                    c.getCouleur(), c.getCapaciteTonnes(), c.getTypeCarburant(), c.getDateAcquisition(),
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
