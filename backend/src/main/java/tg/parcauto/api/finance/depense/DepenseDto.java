package tg.parcauto.api.finance.depense;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import tg.parcauto.api.finance.commun.ModePaiement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class DepenseDto {

    private DepenseDto() {
    }

    /**
     * Saisie d'une depense : un vehicule et sa nature, ou une charge generale
     * (sans vehicule) et sa categorie. Une depense a payer devient une dette
     * envers le fournisseur ; l'acompte eventuel est son premier reglement.
     */
    public record Requete(
            Long camionId,
            Long chauffeurId,
            Long missionId,
            Long locationId,
            SousCategorieDepense sousCategorie,
            @NotBlank @Size(max = 255) String libelle,
            @NotNull @PositiveOrZero BigDecimal montant,
            @NotNull LocalDate dateDepense,
            @Size(max = 180) String fournisseur,
            ModePaiement modePaiement,
            @Size(max = 80) String reference,
            @Size(max = 500) String justificatif,
            @PositiveOrZero Long kilometrage,
            @Size(max = 150) String station,
            @Positive BigDecimal quantiteLitres,
            @PositiveOrZero BigDecimal prixLitre,
            Boolean aChargeClient,
            String observations,
            /** Charge generale : sa categorie (le vehicule reste vide). */
            Long categorieChargeId,
            /** Fiche fournisseur ; a defaut, le nom saisi retrouve ou cree la fiche. */
            Long fournisseurId,
            /** A payer : dette envers le fournisseur. */
            Boolean aPayer,
            LocalDate echeance,
            @PositiveOrZero BigDecimal acompte,
            /** Modele recurrent et mois regle (AAAA-MM). */
            Long recurrenteId,
            @Size(max = 7) String periode) {

        /** Saisie d'une depense de vehicule payee tout de suite (forme historique, utilisee par la maintenance). */
        public Requete(Long camionId, Long chauffeurId, Long missionId, Long locationId,
                       SousCategorieDepense sousCategorie, String libelle, BigDecimal montant, LocalDate dateDepense,
                       String fournisseur, ModePaiement modePaiement, String reference, String justificatif,
                       Long kilometrage, String station, BigDecimal quantiteLitres, BigDecimal prixLitre,
                       Boolean aChargeClient, String observations) {
            this(camionId, chauffeurId, missionId, locationId, sousCategorie, libelle, montant, dateDepense,
                    fournisseur, modePaiement, reference, justificatif, kilometrage, station, quantiteLitres,
                    prixLitre, aChargeClient, observations, null, null, false, null, null, null, null);
        }
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
            Long locationId,
            String locationNumero,
            CategorieDepense categorie,
            String categorieLibelle,
            SousCategorieDepense sousCategorie,
            String sousCategorieLibelle,
            String libelle,
            BigDecimal montant,
            LocalDate dateDepense,
            String fournisseur,
            ModePaiement modePaiement,
            String reference,
            String justificatif,
            Long kilometrage,
            String station,
            BigDecimal quantiteLitres,
            BigDecimal prixLitre,
            boolean aChargeClient,
            String observations,
            /** Charge generale. */
            boolean chargeGenerale,
            Long categorieChargeId,
            String categorieChargeNom,
            String categorieChargeIcone,
            Long fournisseurId,
            String fournisseurNom,
            Long recurrenteId,
            String periode,
            /** Dette : a payer, deja regle, reste du, echeance et retard. */
            boolean aPayer,
            LocalDate echeance,
            BigDecimal regle,
            BigDecimal resteDu,
            boolean enRetard,
            boolean justificatifJoint,
            String justificatifNom,
            StatutDepense statut,
            String statutLibelle,
            String auteurNom,
            Instant creeLe,
            String approuveePar,
            Instant approuveeLe,
            String refuseePar,
            Instant refuseeLe,
            String motifRefus,
            String annuleePar,
            Instant annuleeLe,
            String motifAnnulation,
            /** Correction proposee d'une depense validee, a approuver : « libelle · montant · date ». */
            String correctionProposee) {

        public static Reponse de(Depense d) {
            return de(d, null);
        }

        /** {@code regle} : total des reglements valides, lu a part ; nul pour une depense payee tout de suite. */
        public static Reponse de(Depense d, BigDecimal regle) {
            BigDecimal paye = !d.isAPayer() ? d.getMontant() : regle == null ? BigDecimal.ZERO : regle;
            BigDecimal reste = d.isAPayer() ? d.getMontant().subtract(paye).max(BigDecimal.ZERO) : BigDecimal.ZERO;
            boolean retard = reste.signum() > 0 && d.getEcheance() != null
                    && d.getEcheance().isBefore(LocalDate.now()) && d.getStatut() == StatutDepense.VALIDEE;
            FournisseurParc f = d.getFournisseurParc();
            return new Reponse(
                    d.getId(), d.getNumero(),
                    d.getCamion() == null ? null : d.getCamion().getId(),
                    d.getCamion() == null ? null : d.getCamion().getImmatriculation(),
                    d.getChauffeur() == null ? null : d.getChauffeur().getId(),
                    d.getChauffeur() == null ? null : d.getChauffeur().nomComplet(),
                    d.getMission() == null ? null : d.getMission().getId(),
                    d.getMission() == null ? null : d.getMission().getNumero(),
                    d.getLocation() == null ? null : d.getLocation().getId(),
                    d.getLocation() == null ? null : d.getLocation().getNumero(),
                    d.getCategorie(), d.getCategorie().getLibelle(),
                    d.getSousCategorie(), d.getSousCategorie().getLibelle(),
                    d.getLibelle(), d.getMontant(), d.getDateDepense(),
                    f != null ? f.getNom() : d.getFournisseur(),
                    d.getModePaiement(), d.getReference(), d.getJustificatif(), d.getKilometrage(),
                    d.getStation(), d.getQuantiteLitres(), d.prixLitreCalcule(),
                    d.isAChargeClient(), d.getObservations(),
                    d.estChargeGenerale(),
                    d.getCategorieCharge() == null ? null : d.getCategorieCharge().getId(),
                    d.getCategorieCharge() == null ? null : d.getCategorieCharge().getNom(),
                    d.getCategorieCharge() == null ? null : d.getCategorieCharge().getIcone(),
                    f == null ? null : f.getId(), f == null ? null : f.getNom(),
                    d.getRecurrente() == null ? null : d.getRecurrente().getId(), d.getPeriode(),
                    d.isAPayer(), d.getEcheance(), paye, reste, retard,
                    d.getJustificatifChemin() != null, d.getJustificatifNom(),
                    d.getStatut(), d.getStatut().getLibelle(), d.getAuteurNom(), d.getCreeLe(),
                    d.getApprouveePar(), d.getApprouveeLe(), d.getRefuseePar(), d.getRefuseeLe(), d.getMotifRefus(),
                    d.getAnnuleePar(), d.getAnnuleeLe(), d.getMotifAnnulation(), resumeCorrection(d));
        }

        private static String resumeCorrection(Depense d) {
            if (d.getCorrectionEnAttente() == null) {
                return null;
            }
            try {
                var n = new com.fasterxml.jackson.databind.ObjectMapper().readTree(d.getCorrectionEnAttente());
                return n.path("libelle").asText("") + " · " + n.path("montant").asText("") + " F · "
                        + n.path("dateDepense").asText("");
            } catch (Exception e) {
                return "Correction proposee";
            }
        }
    }

    /** Repartition des depenses par categorie (chapitre 17). */
    public record LigneCategorie(CategorieDepense categorie, String libelle, BigDecimal total, long nombre) {
    }

    /** Repartition fine par nature de depense. */
    public record LigneNature(SousCategorieDepense nature, String libelle, String categorieLibelle,
                              BigDecimal total, long nombre) {
    }

    /** Repartition des charges generales par categorie modifiable. */
    public record LigneCharge(Long categorieId, String categorie, String icone, BigDecimal total, long nombre) {
    }

    public record Motif(@NotBlank @Size(max = 255) String motif) {
    }
}
