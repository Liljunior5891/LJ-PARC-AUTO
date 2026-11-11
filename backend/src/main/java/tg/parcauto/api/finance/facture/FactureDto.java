package tg.parcauto.api.finance.facture;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import tg.parcauto.api.finance.commun.ModePaiement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class FactureDto {

    private FactureDto() {
    }

    public record LigneRequete(
            Long camionId,
            @NotBlank @Size(max = 255) String designation,
            @NotNull @Positive BigDecimal quantite,
            @Size(max = 30) String unite,
            @NotNull @PositiveOrZero BigDecimal prixUnitaire) {
    }

    public record Requete(
            @NotNull Long clientId,
            Long locationId,
            Long missionId,
            @NotNull LocalDate dateEmission,
            LocalDate dateEcheance,
            LocalDate periodeDebut,
            LocalDate periodeFin,
            String observations,
            @NotEmpty @Valid List<LigneRequete> lignes) {
    }

    /** Generation d'une facture a partir d'un contrat de location (chapitre 27). */
    public record DepuisContrat(
            @NotNull Long locationId,
            LocalDate dateEmission,
            Integer delaiPaiementJours) {
    }

    /** Generation d'une facture a partir d'une mission terminee. */
    public record DepuisMission(
            @NotNull Long missionId,
            LocalDate dateEmission,
            Integer delaiPaiementJours) {
    }

    public record PaiementRequete(
            @NotNull @Positive BigDecimal montant,
            @NotNull @jakarta.validation.constraints.PastOrPresent LocalDate datePaiement,
            @NotNull ModePaiement modePaiement,
            @Size(max = 80) String reference,
            String observations) {
    }

    public record LigneReponse(
            Long id,
            Long camionId,
            String camionImmatriculation,
            String designation,
            BigDecimal quantite,
            String unite,
            BigDecimal prixUnitaire,
            BigDecimal montant) {

        public static LigneReponse de(FactureLigne l) {
            return new LigneReponse(
                    l.getId(),
                    l.getCamion() == null ? null : l.getCamion().getId(),
                    l.getCamion() == null ? null : l.getCamion().getImmatriculation(),
                    l.getDesignation(), l.getQuantite(), l.getUnite(),
                    l.getPrixUnitaire(), l.getMontant());
        }
    }

    public record PaiementReponse(
            Long id,
            BigDecimal montant,
            LocalDate datePaiement,
            ModePaiement modePaiement,
            String modeLibelle,
            String reference,
            String observations) {

        public static PaiementReponse de(Paiement p) {
            return new PaiementReponse(p.getId(), p.getMontant(), p.getDatePaiement(),
                    p.getModePaiement(), p.getModePaiement().getLibelle(),
                    p.getReference(), p.getObservations());
        }
    }

    public record Reponse(
            Long id,
            String numero,
            Long clientId,
            String clientNom,
            Long locationId,
            String locationNumero,
            Long missionId,
            String missionNumero,
            StatutFacture statut,
            String statutLibelle,
            LocalDate dateEmission,
            LocalDate dateEcheance,
            LocalDate periodeDebut,
            LocalDate periodeFin,
            BigDecimal montantHt,
            BigDecimal tauxTva,
            BigDecimal montantTva,
            BigDecimal montantTtc,
            BigDecimal montantPaye,
            BigDecimal resteAPayer,
            boolean enRetard,
            String observations,
            List<LigneReponse> lignes,
            List<PaiementReponse> paiements,
            /** Facture d'un contrat ne dans la boutique en ligne. */
            boolean enLigne) {

        /** Vue de liste : ni les lignes ni les paiements ne sont charges. */
        public static Reponse resume(Facture f) {
            return construire(f, List.of(), List.of());
        }

        public static Reponse complete(Facture f) {
            return construire(f,
                    f.getLignes().stream().map(LigneReponse::de).toList(),
                    f.getPaiements().stream().map(PaiementReponse::de).toList());
        }

        private static Reponse construire(Facture f, List<LigneReponse> lignes,
                                          List<PaiementReponse> paiements) {
            return new Reponse(
                    f.getId(), f.getNumero(),
                    f.getClient().getId(), f.getClient().getRaisonSociale(),
                    f.getLocation() == null ? null : f.getLocation().getId(),
                    f.getLocation() == null ? null : f.getLocation().getNumero(),
                    f.getMission() == null ? null : f.getMission().getId(),
                    f.getMission() == null ? null : f.getMission().getNumero(),
                    f.getStatut(), f.getStatut().getLibelle(),
                    f.getDateEmission(), f.getDateEcheance(), f.getPeriodeDebut(), f.getPeriodeFin(),
                    f.getMontantHt(), f.getTauxTva(), f.getMontantTva(), f.getMontantTtc(),
                    f.montantPaye(), f.resteAPayer(), f.enRetard(), f.getObservations(),
                    lignes, paiements, f.isEnLigne() || (f.getLocation() != null && f.getLocation().isEnLigne()));
        }
    }

    /** Etat des creances (chapitre 28). */
    public record Creances(
            long nombreFacturesImpayees,
            BigDecimal montantFacture,
            BigDecimal montantPaye,
            BigDecimal montantRestant,
            long nombreEnRetard) {
    }
}
