package tg.parcauto.api.pilotage;

import tg.parcauto.api.parc.camion.StatutCamion;

import java.util.Map;

public final class TableauBordDto {

    private TableauBordDto() {
    }

    /** Indicateurs de parc du chapitre 4.1. */
    public record Parc(
            long totalCamions,
            long camionsDisponibles,
            long camionsEnLocation,
            long camionsEnMission,
            long camionsEnMaintenance,
            long camionsImmobilises,
            Map<StatutCamion, Long> repartitionStatuts,
            long chauffeursActifs,
            long affectationsEnCours,
            long camionsSansChauffeur,
            long documentsAExpirer,
            long documentsExpires,
            long permisAExpirer) {
    }

    /** Indicateurs financiers du chapitre 4.2. */
    public record Finances(
            java.time.LocalDate debut,
            java.time.LocalDate fin,
            java.math.BigDecimal chiffreAffaires,
            java.math.BigDecimal totalDepenses,
            java.math.BigDecimal resultat,
            java.math.BigDecimal margePourcent,
            java.math.BigDecimal encaisseSurPeriode,
            java.math.BigDecimal creancesRestantes,
            long facturesImpayees,
            long facturesEnRetard,
            java.math.BigDecimal recetteMoyenneParCamion,
            java.math.BigDecimal depenseMoyenneParCamion) {
    }

    /** Indicateurs d'exploitation du chapitre 4.1 (volet locations et missions). */
    public record Exploitation(
            long clientsActifs,
            long contratsActifs,
            long contratsEnAttente,
            long contratsEcheance,
            long missionsEnCours,
            long missionsPlanifiees,
            long livraisonsEnAttente,
            long livraisonsLivrees,
            long tauxUtilisationPourcent) {
    }
}
