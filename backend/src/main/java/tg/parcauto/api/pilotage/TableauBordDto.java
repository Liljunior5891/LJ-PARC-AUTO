package tg.parcauto.api.pilotage;

import tg.parcauto.api.parc.camion.StatutCamion;

import java.util.Map;

public final class TableauBordDto {

    private TableauBordDto() {
    }

    /** Indicateurs parc du chapitre 4.1 disponibles a ce stade du projet. */
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
}
