package tg.parcauto.api.pilotage;

import tg.parcauto.api.finance.depense.DepenseDto;
import tg.parcauto.api.parc.camion.StatutCamion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Fiche synthetique du camion, chapitre 36 du cahier des charges :
 * toutes les informations essentielles rassemblees en un seul endroit.
 */
public record FicheSyntheseDto(
        Long camionId,
        String immatriculation,
        String marqueModele,
        StatutCamion statut,
        String statutLibelle,
        LocalDate debut,
        LocalDate fin,

        // --- Situation actuelle ---
        String chauffeurNom,
        String clientNom,
        String activite,
        String localisation,
        Instant dernierePositionLe,

        // --- Compteurs ---
        long kilometrageActuel,
        long kilometresSurPeriode,
        long nombreMissions,

        // --- Finances de la periode ---
        BigDecimal recettes,
        BigDecimal depenses,
        BigDecimal resultat,
        BigDecimal margePourcent,
        BigDecimal coutParKm,
        BigDecimal revenuParKm,
        List<DepenseDto.LigneCategorie> depensesParCategorie,

        // --- Carburant ---
        BigDecimal consommationMoyenneL100km,
        BigDecimal coutCarburantParKm,

        // --- Etat du vehicule ---
        long entretiensAVenir,
        long entretiensEnRetard,
        long pneusARemplacer,
        long incidentsOuverts,
        long documentsAExpirer,
        long alertesGpsNonTraitees) {
}
