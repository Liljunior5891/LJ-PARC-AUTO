package tg.parcauto.api.pilotage;

import tg.parcauto.api.parc.camion.StatutCamion;

/**
 * Ligne de la vue globale du parc, chapitre 5 du cahier des charges.
 * <p>
 * Les colonnes financieres (recettes, depenses, resultat) seront alimentees
 * par les modules Recettes et Depenses de la phase 3.
 */
public record VueParcDto(
        Long camionId,
        String immatriculation,
        String marqueModele,
        StatutCamion statut,
        String statutLibelle,
        Long chauffeurId,
        String chauffeurNom,
        String clientNom,
        String activite,
        String localisation,
        Long kilometrageActuel) {
}
