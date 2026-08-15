package tg.parcauto.api.parc.affectation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class AffectationDto {

    private AffectationDto() {
    }

    public record Requete(
            @NotNull Long camionId,
            @NotNull Long chauffeurId,
            @NotNull LocalDate dateDebut,
            LocalDate dateFin,
            @Size(max = 255) String motif,
            String observations) {
    }

    public record Cloture(@NotNull LocalDate dateFin, String observations) {
    }

    public record Reponse(
            Long id,
            Long camionId,
            String camionImmatriculation,
            String camionNumeroInterne,
            Long chauffeurId,
            String chauffeurNom,
            LocalDate dateDebut,
            LocalDate dateFin,
            boolean enCours,
            String motif,
            String observations) {

        public static Reponse de(Affectation a) {
            return new Reponse(
                    a.getId(),
                    a.getCamion().getId(),
                    a.getCamion().getImmatriculation(),
                    a.getCamion().getNumeroInterne(),
                    a.getChauffeur().getId(),
                    a.getChauffeur().nomComplet(),
                    a.getDateDebut(),
                    a.getDateFin(),
                    a.enCours(),
                    a.getMotif(),
                    a.getObservations());
        }
    }
}
