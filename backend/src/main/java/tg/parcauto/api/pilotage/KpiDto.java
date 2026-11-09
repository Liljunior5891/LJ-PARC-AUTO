package tg.parcauto.api.pilotage;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Indicateurs de pilotage de la direction, chapitre 41 du cahier des charges.
 * Les dix-sept indicateurs listes au cahier des charges sont regroupes par famille.
 */
public record KpiDto(
        LocalDate debut,
        LocalDate fin,

        // --- Parc ---
        long nombreCamions,
        long camionsActifs,
        BigDecimal tauxUtilisationPourcent,
        BigDecimal tauxDisponibilitePourcent,
        long joursImmobilisation,

        // --- Finances ---
        BigDecimal chiffreAffaires,
        BigDecimal depenses,
        BigDecimal resultat,
        BigDecimal margePourcent,
        BigDecimal recetteMoyenneParCamion,
        BigDecimal depenseMoyenneParCamion,
        BigDecimal coutParKm,
        BigDecimal revenuParKm,
        BigDecimal coutMaintenance,
        /** Charges generales (sans vehicule) et resultat de l'entreprise apres ces charges. */
        BigDecimal chargesGenerales,
        BigDecimal resultatNet,

        // --- Exploitation ---
        long kilometresParcourus,
        long nombreMissions,
        BigDecimal consommationMoyenneL100km) {
}
