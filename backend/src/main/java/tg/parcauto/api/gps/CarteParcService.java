package tg.parcauto.api.gps;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.exploitation.mission.Mission;
import tg.parcauto.api.exploitation.mission.MissionRepository;
import tg.parcauto.api.gps.position.PositionGps;
import tg.parcauto.api.gps.position.PositionGpsDto;
import tg.parcauto.api.gps.position.PositionGpsService;
import tg.parcauto.api.parc.affectation.Affectation;
import tg.parcauto.api.parc.affectation.AffectationRepository;
import tg.parcauto.api.parc.camion.CamionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Carte du parc et historique des trajets, chapitres 23 et 24 du cahier des charges.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CarteParcService {

    /** En dessous de cette vitesse, le vehicule est considere a l'arret. */
    private static final double SEUIL_ARRET_KMH = 3d;
    /** Duree minimale d'immobilisation pour compter un arret. */
    private static final Duration DUREE_ARRET = Duration.ofMinutes(5);

    private final PositionGpsService positionService;
    private final MissionRepository missionRepository;
    private final AffectationRepository affectationRepository;
    private final CamionService camionService;

    /** Marqueurs a afficher sur la carte, un par camion equipe. */
    public List<PositionGpsDto.MarqueurCarte> marqueurs() {
        Map<Long, Mission> missions = missionRepository.missionsEnCours().stream()
                .collect(Collectors.toMap(m -> m.getCamion().getId(), Function.identity(), (a, b) -> a));
        Map<Long, Affectation> affectations = affectationRepository.affectationsEnCours().stream()
                .collect(Collectors.toMap(a -> a.getCamion().getId(), Function.identity(), (a, b) -> a));

        return positionService.dernieresPositions().stream()
                .map(position -> {
                    var camion = position.getCamion();
                    Mission mission = missions.get(camion.getId());
                    Affectation affectation = affectations.get(camion.getId());
                    String chauffeur = mission != null && mission.getChauffeur() != null
                            ? mission.getChauffeur().nomComplet()
                            : affectation != null ? affectation.getChauffeur().nomComplet() : null;
                    String activite = mission == null ? null
                            : mission.getTypeMission().getLibelle() + " (" + mission.getNumero() + ")";

                    return new PositionGpsDto.MarqueurCarte(
                            camion.getId(), camion.getImmatriculation(), camion.libelleMarque(),
                            camion.getStatut(), camion.getStatut().getLibelle(),
                            chauffeur, activite,
                            position.getLatitude(), position.getLongitude(),
                            position.getVitesseKmh(), position.getDirection(),
                            position.getHorodatage(), positionService.horsConnexion(position));
                })
                .sorted(Comparator.comparing(PositionGpsDto.MarqueurCarte::immatriculation))
                .toList();
    }

    /**
     * Reconstitue un trajet : itineraire, distance cumulee, vitesses et arrets
     * (chapitre 24). La distance est calculee de point a point.
     */
    public PositionGpsDto.Trajet trajet(Long camionId, java.time.Instant debut, java.time.Instant fin) {
        var camion = camionService.charger(camionId);
        List<PositionGps> points = positionService.trajet(camionId, debut, fin);

        double distanceMetres = 0d;
        double sommeVitesses = 0d;
        int mesuresVitesse = 0;
        BigDecimal vitesseMax = BigDecimal.ZERO;
        int arrets = 0;

        PositionGps precedent = null;
        java.time.Instant debutImmobilite = null;
        for (PositionGps point : points) {
            if (precedent != null) {
                distanceMetres += precedent.distanceMetres(point.getLatitude(), point.getLongitude());
            }
            if (point.getVitesseKmh() != null) {
                sommeVitesses += point.getVitesseKmh().doubleValue();
                mesuresVitesse++;
                vitesseMax = vitesseMax.max(point.getVitesseKmh());
            }

            boolean immobile = point.getVitesseKmh() == null
                    || point.getVitesseKmh().doubleValue() < SEUIL_ARRET_KMH;
            if (immobile) {
                if (debutImmobilite == null) {
                    debutImmobilite = point.getHorodatage();
                }
            } else {
                if (debutImmobilite != null
                        && Duration.between(debutImmobilite, point.getHorodatage())
                                .compareTo(DUREE_ARRET) >= 0) {
                    arrets++;
                }
                debutImmobilite = null;
            }
            precedent = point;
        }
        // Un arret toujours en cours a la fin de la periode compte egalement.
        if (debutImmobilite != null && precedent != null
                && Duration.between(debutImmobilite, precedent.getHorodatage())
                        .compareTo(DUREE_ARRET) >= 0) {
            arrets++;
        }

        java.time.Instant premier = points.isEmpty() ? debut : points.get(0).getHorodatage();
        java.time.Instant dernier = points.isEmpty() ? fin : points.get(points.size() - 1).getHorodatage();

        return new PositionGpsDto.Trajet(
                camion.getId(), camion.getImmatriculation(), premier, dernier,
                points.size(),
                Math.round(distanceMetres / 1000),
                mesuresVitesse == 0 ? BigDecimal.ZERO
                        : BigDecimal.valueOf(sommeVitesses / mesuresVitesse)
                                .setScale(1, RoundingMode.HALF_UP),
                vitesseMax,
                Duration.between(premier, dernier).toMinutes(),
                arrets,
                points.stream().map(PositionGpsDto.Reponse::de).toList());
    }
}
