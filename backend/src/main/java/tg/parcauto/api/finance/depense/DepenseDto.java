package tg.parcauto.api.finance.depense;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import tg.parcauto.api.finance.commun.ModePaiement;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class DepenseDto {

    private DepenseDto() {
    }

    public record Requete(
            @NotNull Long camionId,
            Long chauffeurId,
            Long missionId,
            Long locationId,
            @NotNull SousCategorieDepense sousCategorie,
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
            String observations) {
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
            String observations) {

        public static Reponse de(Depense d) {
            return new Reponse(
                    d.getId(), d.getNumero(),
                    d.getCamion().getId(), d.getCamion().getImmatriculation(),
                    d.getChauffeur() == null ? null : d.getChauffeur().getId(),
                    d.getChauffeur() == null ? null : d.getChauffeur().nomComplet(),
                    d.getMission() == null ? null : d.getMission().getId(),
                    d.getMission() == null ? null : d.getMission().getNumero(),
                    d.getLocation() == null ? null : d.getLocation().getId(),
                    d.getLocation() == null ? null : d.getLocation().getNumero(),
                    d.getCategorie(), d.getCategorie().getLibelle(),
                    d.getSousCategorie(), d.getSousCategorie().getLibelle(),
                    d.getLibelle(), d.getMontant(), d.getDateDepense(), d.getFournisseur(),
                    d.getModePaiement(), d.getReference(), d.getJustificatif(), d.getKilometrage(),
                    d.getStation(), d.getQuantiteLitres(), d.prixLitreCalcule(),
                    d.isAChargeClient(), d.getObservations());
        }
    }

    /** Repartition des depenses par categorie (chapitre 17). */
    public record LigneCategorie(CategorieDepense categorie, String libelle, BigDecimal total, long nombre) {
    }

    /** Repartition fine par nature de depense. */
    public record LigneNature(SousCategorieDepense nature, String libelle, String categorieLibelle,
                              BigDecimal total, long nombre) {
    }
}
