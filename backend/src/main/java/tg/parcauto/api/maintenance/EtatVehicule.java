package tg.parcauto.api.maintenance;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tg.parcauto.api.exploitation.location.LocationRepository;
import tg.parcauto.api.exploitation.mission.MissionRepository;
import tg.parcauto.api.maintenance.entretien.EntretienRepository;
import tg.parcauto.api.maintenance.entretien.StatutEntretien;
import tg.parcauto.api.maintenance.incident.IncidentRepository;
import tg.parcauto.api.parc.camion.Camion;
import tg.parcauto.api.parc.camion.StatutCamion;

/**
 * Statut d'un vehicule recalcule d'apres ce qui le retient vraiment, quand un
 * incident se resout ou qu'une intervention se termine : plutot que de le
 * remettre « disponible » d'office (alors qu'il est encore loue, en mission ou
 * accidente par ailleurs), on regarde, dans l'ordre :
 * <ol>
 *   <li>une intervention encore a l'atelier : en maintenance ;</li>
 *   <li>un incident immobilisant encore ouvert : accidente ou immobilise ;</li>
 *   <li>une mission en cours (ou un contrat actif) : en mission / en location ;</li>
 *   <li>sinon : disponible.</li>
 * </ol>
 * Un vehicule vendu ou hors service n'est jamais touche.
 */
@Component
@RequiredArgsConstructor
public class EtatVehicule {

    private final EntretienRepository entretiens;
    private final IncidentRepository incidents;
    private final MissionRepository missions;
    private final LocationRepository locations;

    public void recalculer(Camion camion) {
        if (!camion.getStatut().exploitable()) {
            return;
        }
        camion.setStatut(statutDe(camion.getId()));
    }

    private StatutCamion statutDe(Long camionId) {
        if (entretiens.existsByCamionIdAndStatut(camionId, StatutEntretien.EN_COURS)) {
            return StatutCamion.EN_MAINTENANCE;
        }
        if (incidents.compterImmobilisantsOuverts(camionId, true) > 0) {
            return StatutCamion.ACCIDENTE;
        }
        if (incidents.compterImmobilisantsOuverts(camionId, false) > 0) {
            return StatutCamion.IMMOBILISE;
        }
        boolean contrat = locations.locationActiveDuCamion(camionId).isPresent();
        if (missions.missionEnCoursDuCamion(camionId).isPresent()) {
            // Une mission sur un vehicule loue le laisse « en location » (regle des missions).
            return contrat ? StatutCamion.EN_LOCATION : StatutCamion.EN_MISSION;
        }
        return contrat ? StatutCamion.EN_LOCATION : StatutCamion.DISPONIBLE;
    }
}
