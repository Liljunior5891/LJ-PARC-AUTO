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
