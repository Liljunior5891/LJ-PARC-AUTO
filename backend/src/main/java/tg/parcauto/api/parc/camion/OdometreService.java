package tg.parcauto.api.parc.camion;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.exploitation.mission.Mission;
import tg.parcauto.api.exploitation.mission.MissionRepository;
import tg.parcauto.api.finance.depense.CategorieDepense;
import tg.parcauto.api.finance.depense.Depense;
import tg.parcauto.api.finance.depense.DepenseRepository;
import tg.parcauto.api.finance.depense.DepenseSpecifications;
import tg.parcauto.api.gps.position.PositionGps;
import tg.parcauto.api.gps.position.PositionGpsRepository;
import tg.parcauto.api.maintenance.entretien.Entretien;
import tg.parcauto.api.maintenance.entretien.EntretienRepository;
import tg.parcauto.api.maintenance.entretien.StatutEntretien;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Historique des releves kilometriques d'un camion.
 * <p>
 * Aucune table dediee : les releves existent deja, disperses dans les missions,
 * les pleins de carburant, les interventions et les trames GPS. Ce service les
 * rassemble et en deduit la distance parcourue entre deux relevés.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OdometreService {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    /** Nombre de trames GPS retenues : au-dela, la liste devient illisible. */
    private static final int POSITIONS_MAX = 50;

    private final CamionService camionService;
    private final MissionRepository missionRepository;
    private final DepenseRepository depenseRepository;
    private final EntretienRepository entretienRepository;
    private final PositionGpsRepository positionRepository;

    public OdometreDto historique(Long camionId, LocalDate debut, LocalDate fin) {
        Camion camion = camionService.charger(camionId);
        Instant debutInstant = debut.atStartOfDay(ZONE).toInstant();
        Instant finInstant = fin.plusDays(1).atStartOfDay(ZONE).toInstant();

        List<OdometreDto.Releve> releves = new ArrayList<>();

        // Missions : deux releves, au depart et a l'arrivee.
        for (Mission mission : missionRepository.missionsTermineesDuCamion(
                camionId, debutInstant, finInstant)) {
            if (mission.getKilometrageDepart() != null) {
                releves.add(new OdometreDto.Releve(
                        LocalDate.ofInstant(mission.getDateDepart(), ZONE),
                        mission.getKilometrageDepart(), null,
                        OdometreDto.Source.MISSION,
                        "Depart de la mission " + mission.getNumero(),
                        mission.getNumero(),
                        mission.getChauffeur() == null ? null : mission.getChauffeur().nomComplet()));
            }
            if (mission.getKilometrageArrivee() != null && mission.getDateArrivee() != null) {
                releves.add(new OdometreDto.Releve(
                        LocalDate.ofInstant(mission.getDateArrivee(), ZONE),
                        mission.getKilometrageArrivee(), null,
                        OdometreDto.Source.MISSION,
                        "Arrivee de la mission " + mission.getNumero(),
                        mission.getNumero(),
                        mission.getChauffeur() == null ? null : mission.getChauffeur().nomComplet()));
            }
        }

        // Depenses portant un releve : pleins de carburant surtout.
        depenseRepository.findAll(DepenseSpecifications.rechercher(
                        null, null, null, camionId, null, debut, fin))
                .stream()
                .filter(depense -> depense.getKilometrage() != null)
                .forEach(depense -> releves.add(new OdometreDto.Releve(
                        depense.getDateDepense(), depense.getKilometrage(), null,
                        depense.getCategorie() == CategorieDepense.CARBURANT
                                ? OdometreDto.Source.CARBURANT : OdometreDto.Source.DEPENSE,
                        libelleDepense(depense), depense.getNumero(),
                        depense.getChauffeur() == null ? null : depense.getChauffeur().nomComplet())));

        // Interventions realisees : releve de l'atelier.
        entretienRepository.historiqueDuCamion(camionId).stream()
                .filter(e -> e.getStatut() == StatutEntretien.REALISE)
                .filter(e -> e.getKilometrageRealise() != null && e.getDateRealisation() != null)
                .filter(e -> !e.getDateRealisation().isBefore(debut) && !e.getDateRealisation().isAfter(fin))
                .forEach(e -> releves.add(new OdometreDto.Releve(
                        e.getDateRealisation(), e.getKilometrageRealise(), null,
                        OdometreDto.Source.ENTRETIEN,
                        e.getTypeLibelleCourt(), e.getNumero(), null)));

        // Trames GPS transmettant un odometre.
        positionRepository.dernieres(camionId, PageRequest.of(0, POSITIONS_MAX)).stream()
                .filter(position -> position.getKilometrage() != null)
                .filter(position -> !position.getHorodatage().isBefore(debutInstant)
                        && position.getHorodatage().isBefore(finInstant))
                .forEach(position -> releves.add(new OdometreDto.Releve(
                        LocalDate.ofInstant(position.getHorodatage(), ZONE),
                        position.getKilometrage(), null,
                        OdometreDto.Source.GPS,
                        "Traceur " + (position.getFournisseur() == null
                                ? "" : position.getFournisseur()),
                        position.getDeviceId(), null)));

        releves.sort(Comparator.comparing(OdometreDto.Releve::date)
                .thenComparing(OdometreDto.Releve::kilometrage));

        // La distance se deduit de l'ecart avec le releve precedent.
        List<OdometreDto.Releve> avecDistance = new ArrayList<>();
        Long precedent = null;
        for (OdometreDto.Releve releve : releves) {
            Long distance = precedent == null ? null : Math.max(0L, releve.kilometrage() - precedent);
            avecDistance.add(new OdometreDto.Releve(releve.date(), releve.kilometrage(), distance,
                    releve.source(), releve.libelle(), releve.reference(), releve.chauffeurNom()));
            precedent = releve.kilometrage();
        }

        long premier = avecDistance.isEmpty() ? camion.getKilometrageActuel()
                : avecDistance.get(0).kilometrage();
        long dernier = avecDistance.isEmpty() ? camion.getKilometrageActuel()
                : avecDistance.get(avecDistance.size() - 1).kilometrage();
        long jours = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(debut, fin) + 1);

        return new OdometreDto(
                camion.getId(), camion.getImmatriculation(), debut, fin,
                camion.getKilometrageActuel(),
                Math.max(0L, dernier - premier),
                avecDistance.size(),
                Math.round((double) Math.max(0L, dernier - premier) / jours),
                avecDistance.reversed());
    }

    private String libelleDepense(Depense depense) {
        if (depense.getCategorie() == CategorieDepense.CARBURANT && depense.getQuantiteLitres() != null) {
            return "Plein de " + depense.getQuantiteLitres().stripTrailingZeros().toPlainString()
                    + " l" + (depense.getStation() == null ? "" : " — " + depense.getStation());
        }
        return depense.getLibelle();
    }
}
