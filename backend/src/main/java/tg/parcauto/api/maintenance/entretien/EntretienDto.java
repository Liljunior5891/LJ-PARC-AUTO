package tg.parcauto.api.maintenance.entretien;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class EntretienDto {

    private EntretienDto() {
    }

    public record Requete(
            @NotNull Long camionId,
            @NotNull TypeEntretien type,
            @NotBlank @Size(max = 255) String libelle,
            LocalDate datePrevue,
            @PositiveOrZero Long kilometragePrevu,
            @Positive Long periodiciteKm,
            @Positive Integer periodiciteJours,
            @Size(max = 180) String fournisseur,
            @Size(max = 180) String intervenant,
            String observations) {
    }

    /** Realisation de l'intervention : releves, cout et pieces remplacees. */
    public record Realisation(
            @NotNull LocalDate dateRealisation,
            @NotNull @PositiveOrZero Long kilometrageRealise,
            @PositiveOrZero BigDecimal cout,
            @Size(max = 180) String fournisseur,
            String piecesRemplacees,
            @PositiveOrZero Integer immobilisationJours,
            String observations,
            /** Genere la depense correspondante pour alimenter la rentabilite. */
            Boolean genererDepense,
            /** Cree l'occurrence suivante d'apres la periodicite. */
            Boolean planifierSuivant) {
    }

    public record Reponse(
            Long id,
            String numero,
            Long camionId,
            String camionImmatriculation,
            Long camionKilometrage,
            Long depenseId,
            String depenseNumero,
            TypeEntretien type,
            String typeLibelle,
            StatutEntretien statut,
            String statutLibelle,
            String libelle,
            LocalDate datePrevue,
            LocalDate dateRealisation,
            Long kilometragePrevu,
            Long kilometrageRealise,
            Long kilometresRestants,
            Long joursRestants,
            boolean echeanceProche,
            boolean enRetard,
            Long periodiciteKm,
            Integer periodiciteJours,
            BigDecimal cout,
            String fournisseur,
            String intervenant,
            String piecesRemplacees,
            Integer immobilisationJours,
            String observations) {

        public static Reponse de(Entretien e) {
            return new Reponse(
                    e.getId(), e.getNumero(),
                    e.getCamion().getId(), e.getCamion().getImmatriculation(),
                    e.getCamion().getKilometrageActuel(),
                    e.getDepense() == null ? null : e.getDepense().getId(),
                    e.getDepense() == null ? null : e.getDepense().getNumero(),
                    e.getType(), e.getType().getLibelle(),
                    e.getStatut(), e.getStatut().getLibelle(), e.getLibelle(),
                    e.getDatePrevue(), e.getDateRealisation(),
                    e.getKilometragePrevu(), e.getKilometrageRealise(),
                    e.kilometresRestants(), e.joursRestants(),
                    e.echeanceProche(), e.enRetard(),
                    e.getPeriodiciteKm(), e.getPeriodiciteJours(),
                    e.getCout(), e.getFournisseur(), e.getIntervenant(),
                    e.getPiecesRemplacees(), e.getImmobilisationJours(), e.getObservations());
        }
    }
}
