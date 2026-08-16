package tg.parcauto.api.maintenance.pneu;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class PneuDto {

    private PneuDto() {
    }

    public record Requete(
            @NotBlank @Size(max = 60) String reference,
            @Size(max = 80) String marque,
            @Size(max = 40) String dimension,
            @Size(max = 80) String numeroSerie,
            LocalDate dateAchat,
            @PositiveOrZero BigDecimal prixAchat,
            @Positive Long dureeViePrevueKm,
            EtatPneu etat,
            String observations) {
    }

    /** Montage sur un camion, a une position donnee. */
    public record Montage(
            @NotNull Long camionId,
            @NotBlank @Size(max = 30) String position,
            @NotNull LocalDate dateMontage,
            @PositiveOrZero Long kilometrageMontage) {
    }

    /** Depose : le pneu revient en stock, part au rebut ou est repare. */
    public record Depose(
            @NotNull LocalDate dateDepose,
            @PositiveOrZero Long kilometrageDepose,
            @NotNull EtatPneu etat,
            @Size(max = 255) String motif,
            /** Vrai si le pneu est mis au rebut plutot que retourne en stock. */
            Boolean rebuter) {
    }

    public record Reponse(
            Long id,
            String reference,
            Long camionId,
            String camionImmatriculation,
            String marque,
            String dimension,
            String numeroSerie,
            String position,
            StatutPneu statut,
            String statutLibelle,
            EtatPneu etat,
            String etatLibelle,
            LocalDate dateAchat,
            BigDecimal prixAchat,
            LocalDate dateMontage,
            Long kilometrageMontage,
            LocalDate dateDepose,
            Long kilometrageDepose,
            Long dureeViePrevueKm,
            Long kilometresParcourus,
            Integer usurePourcent,
            boolean aRemplacer,
            BigDecimal coutParKm,
            String motifDepose,
            String observations) {

        public static Reponse de(Pneu p) {
            return new Reponse(
                    p.getId(), p.getReference(),
                    p.getCamion() == null ? null : p.getCamion().getId(),
                    p.getCamion() == null ? null : p.getCamion().getImmatriculation(),
                    p.getMarque(), p.getDimension(), p.getNumeroSerie(), p.getPosition(),
                    p.getStatut(), p.getStatut().getLibelle(),
                    p.getEtat(), p.getEtat().getLibelle(),
                    p.getDateAchat(), p.getPrixAchat(),
                    p.getDateMontage(), p.getKilometrageMontage(),
                    p.getDateDepose(), p.getKilometrageDepose(),
                    p.getDureeViePrevueKm(), p.kilometresParcourus(), p.usurePourcent(),
                    p.aRemplacer(), p.coutParKm(), p.getMotifDepose(), p.getObservations());
        }
    }
}
