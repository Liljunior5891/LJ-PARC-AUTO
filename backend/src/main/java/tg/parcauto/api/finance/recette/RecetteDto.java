package tg.parcauto.api.finance.recette;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import tg.parcauto.api.finance.commun.ModePaiement;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class RecetteDto {

    private RecetteDto() {
    }

    public record Requete(
            @NotNull Long camionId,
            Long clientId,
            Long locationId,
            Long missionId,
            Long factureId,
            @NotNull TypeRecette type,
            @NotBlank @Size(max = 255) String libelle,
            @NotNull @PositiveOrZero BigDecimal montant,
            @NotNull LocalDate dateRecette,
            ModePaiement modePaiement,
            @Size(max = 80) String reference,
            @Size(max = 500) String justificatif,
            String observations,
            LocalDate dateDebut,
            LocalDate dateFin,
            @Size(max = 180) String lieuDepart,
            @Size(max = 180) String destination,
            @Size(max = 255) String marchandise,
            @PositiveOrZero Long kilometres) {
    }

    /**
     * Recette de location saisie avec les conditions du contrat.
     * <p>
     * Le montant n'est pas transmis : il se deduit du modele tarifaire, de la
     * periode et du tarif, exactement comme sur un contrat.
     */
    public record RequeteLocation(
            @NotNull Long camionId,
            @NotNull Long clientId,
            Long chauffeurId,
            @NotNull tg.parcauto.api.exploitation.location.TypeLocation typeLocation,
            @NotNull LocalDate dateDebut,
            LocalDate dateFin,
            @NotNull @PositiveOrZero BigDecimal tarif,
            @PositiveOrZero Long kilometrage,
            @Size(max = 180) String lieuDepart,
            @Size(max = 180) String destination,
            @NotNull LocalDate dateRecette,
            ModePaiement modePaiement,
            @Size(max = 80) String reference,
            String observations) {
    }

    public record Reponse(
            Long id,
            String numero,
            Long camionId,
            String camionImmatriculation,
            Long clientId,
            String clientNom,
            Long locationId,
            String locationNumero,
            Long missionId,
            String missionNumero,
            Long factureId,
            String factureNumero,
            TypeRecette type,
            String typeLibelle,
            String libelle,
            BigDecimal montant,
            LocalDate dateRecette,
            ModePaiement modePaiement,
            String reference,
            String justificatif,
            String observations,
            LocalDate dateDebut,
            LocalDate dateFin,
            String lieuDepart,
            String destination,
            String marchandise,
            Long kilometres) {

        public static Reponse de(Recette r) {
            return new Reponse(
                    r.getId(), r.getNumero(),
                    r.getCamion().getId(), r.getCamion().getImmatriculation(),
                    r.getClient() == null ? null : r.getClient().getId(),
                    r.getClient() == null ? null : r.getClient().getRaisonSociale(),
                    r.getLocation() == null ? null : r.getLocation().getId(),
                    r.getLocation() == null ? null : r.getLocation().getNumero(),
                    r.getMission() == null ? null : r.getMission().getId(),
                    r.getMission() == null ? null : r.getMission().getNumero(),
                    r.getFacture() == null ? null : r.getFacture().getId(),
                    r.getFacture() == null ? null : r.getFacture().getNumero(),
                    r.getType(), r.getType().getLibelle(), r.getLibelle(), r.getMontant(),
                    r.getDateRecette(), r.getModePaiement(), r.getReference(),
                    r.getJustificatif(), r.getObservations(),
                    r.getDateDebut(), r.getDateFin(), r.getLieuDepart(), r.getDestination(),
                    r.getMarchandise(), r.getKilometres());
        }
    }

    /** Repartition des recettes par type sur une periode. */
    public record LigneType(TypeRecette type, String libelle, BigDecimal total, long nombre) {
    }
}
