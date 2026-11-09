package tg.parcauto.api.finance.rentabilite;

import tg.parcauto.api.finance.depense.DepenseDto;
import tg.parcauto.api.finance.recette.RecetteDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class RentabiliteDto {

    private RentabiliteDto() {
    }

    /** Ligne de comparaison entre camions, chapitres 29 et 30. */
    public record LigneCamion(
            Long camionId,
            String immatriculation,
            String marque,
            String statutLibelle,
            BigDecimal recettes,
            BigDecimal depenses,
            BigDecimal resultat,
            BigDecimal margePourcent,
            long distanceKm,
            long nombreMissions,
            BigDecimal coutParKm,
            BigDecimal revenuParKm) {
    }

    /** Rentabilite globale du parc, chapitre 29. */
    public record Global(
            LocalDate debut,
            LocalDate fin,
            BigDecimal totalRecettes,
            BigDecimal totalDepenses,
            BigDecimal resultat,
            BigDecimal margePourcent,
            long nombreCamions,
            BigDecimal recetteMoyenneParCamion,
            BigDecimal depenseMoyenneParCamion,
            List<LigneCamion> camions,
            Palmares palmares,
            /** Charges generales de l'entreprise (sans vehicule), hors marge des vehicules. */
            BigDecimal chargesGenerales,
            /** Resultat de l'entreprise : resultat des vehicules moins les charges generales. */
            BigDecimal resultatNet) {
    }

    /** Comparaison des camions, chapitre 30. */
    public record Palmares(
            LigneCamion plusRentable,
            LigneCamion moinsRentable,
            LigneCamion plusDeRecettes,
            LigneCamion plusDeDepenses,
            LigneCamion plusDeKilometres) {
    }

    /** Revenus par horizon, chapitre 7. */
    public record Revenus(
            BigDecimal jour,
            BigDecimal semaine,
            BigDecimal mois,
            BigDecimal annee,
            BigDecimal cumule) {
    }

    /** Utilisation du vehicule, chapitre 7. */
    public record Utilisation(
            long joursPeriode,
            long joursExploites,
            long joursDisponibles,
            long joursImmobilises,
            long distanceKm,
            long nombreMissions,
            BigDecimal tauxUtilisationPourcent) {
    }

    /** Fiche « performance du camion », chapitre 7 du cahier des charges. */
    public record FicheCamion(
            Long camionId,
            String immatriculation,
            String marque,
            String marqueModele,
            String statutLibelle,
            LocalDate debut,
            LocalDate fin,
            Revenus revenus,
            BigDecimal totalRecettes,
            BigDecimal totalDepenses,
            BigDecimal resultat,
            BigDecimal margePourcent,
            BigDecimal coutParKm,
            BigDecimal revenuParKm,
            Utilisation utilisation,
            List<RecetteDto.LigneType> recettesParType,
            List<DepenseDto.LigneCategorie> depensesParCategorie,
            List<DepenseDto.LigneNature> depensesParNature) {
    }
}
