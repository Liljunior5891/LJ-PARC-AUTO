package tg.parcauto.api.gps.alerte;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.exploitation.mission.MissionRepository;
import tg.parcauto.api.gps.position.PositionGps;
import tg.parcauto.api.gps.position.PositionGpsRepository;
import tg.parcauto.api.gps.zone.ZoneGeofence;
import tg.parcauto.api.gps.zone.ZoneGeofenceRepository;
import tg.parcauto.api.parc.camion.Camion;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Geofencing et alertes GPS, chapitres 25 et 26 du cahier des charges.
 * <p>
 * Chaque position recue est confrontee aux zones surveillees et aux regles
 * d'exploitation. Une meme alerte n'est pas repetee tant que la fenetre
 * d'anti-repetition n'est pas ecoulee : sans cela, un camion arrete dans une
 * zone genererait une alerte a chaque trame.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SurveillanceGpsService {

    /** Duree pendant laquelle une alerte identique n'est pas repetee. */
    private static final Duration ANTI_REPETITION = Duration.ofMinutes(30);
    /** Vitesse retenue quand aucun seuil n'est defini sur le camion. */
    private static final int VITESSE_MAX_PAR_DEFAUT = 90;
    private static final ZoneId ZONE_LOCALE = ZoneId.systemDefault();

    private final AlerteGpsRepository repository;
    private final ZoneGeofenceRepository zoneRepository;
    private final PositionGpsRepository positionRepository;
    private final MissionRepository missionRepository;

    @Value("${parcauto.gps.heure-debut-service:5}")
    private int heureDebutService;

    @Value("${parcauto.gps.heure-fin-service:21}")
    private int heureFinService;

    /** Analyse une position et enregistre les alertes qu'elle declenche. */
    public List<AlerteGps> analyser(PositionGps position, PositionGps precedente) {
        List<AlerteGps> alertes = new ArrayList<>();

        verifierVitesse(position).ifPresent(alertes::add);
        alertes.addAll(verifierZones(position, precedente));
        verifierHorairesDeService(position).ifPresent(alertes::add);
        verifierMouvementHorsMission(position).ifPresent(alertes::add);

        return alertes.isEmpty() ? List.of() : repository.saveAll(alertes);
    }

    /** Exces de vitesse : seuil propre au camion, sinon seuil du parc. */
    private java.util.Optional<AlerteGps> verifierVitesse(PositionGps position) {
        if (position.getVitesseKmh() == null) {
            return java.util.Optional.empty();
        }
        Camion camion = position.getCamion();
        int seuil = camion.getVitesseMaxKmh() == null ? VITESSE_MAX_PAR_DEFAUT : camion.getVitesseMaxKmh();
        if (position.getVitesseKmh().doubleValue() <= seuil) {
            return java.util.Optional.empty();
        }
        if (dejaAlerte(camion.getId(), TypeAlerteGps.EXCES_VITESSE, position.getHorodatage())) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(construire(position, TypeAlerteGps.EXCES_VITESSE, null,
                "%s roule a %s km/h (limite %d km/h)".formatted(
                        camion.getImmatriculation(), position.getVitesseKmh(), seuil)));
    }

    /** Entrees, sorties et stationnements prolonges dans les zones surveillees. */
    private List<AlerteGps> verifierZones(PositionGps position, PositionGps precedente) {
        List<AlerteGps> alertes = new ArrayList<>();
        for (ZoneGeofence zone : zoneRepository.findByActifTrueOrderByNomAsc()) {
            boolean dedans = dansLaZone(position, zone);
            boolean etaitDedans = precedente != null && dansLaZone(precedente, zone);

            if (dedans && !etaitDedans) {
                if (zone.getType().surveillanceObligatoire()) {
                    alertes.add(construire(position, TypeAlerteGps.ZONE_INTERDITE, zone,
                            "%s est entre dans la zone interdite %s".formatted(
                                    position.getCamion().getImmatriculation(), zone.getNom())));
                } else if (zone.isAlerteEntree()) {
                    alertes.add(construire(position, TypeAlerteGps.ENTREE_ZONE, zone,
                            "%s est entre dans la zone %s".formatted(
                                    position.getCamion().getImmatriculation(), zone.getNom())));
                }
            } else if (!dedans && etaitDedans && zone.isAlerteSortie()) {
                alertes.add(construire(position, TypeAlerteGps.SORTIE_ZONE, zone,
                        "%s a quitte la zone %s".formatted(
                                position.getCamion().getImmatriculation(), zone.getNom())));
            } else if (dedans && zone.getDureeMaxMinutes() != null) {
                verifierStationnement(position, zone).ifPresent(alertes::add);
            }
        }
        return alertes;
    }

    /**
     * Arret prolonge : le vehicule est immobile dans la zone depuis plus longtemps
     * que la duree toleree.
     */
    private java.util.Optional<AlerteGps> verifierStationnement(PositionGps position, ZoneGeofence zone) {
        if (position.enMouvement()) {
            return java.util.Optional.empty();
        }
        Instant limite = position.getHorodatage().minus(Duration.ofMinutes(zone.getDureeMaxMinutes()));
        List<PositionGps> anterieures = positionRepository.trajet(
                position.getCamion().getId(), limite, position.getHorodatage());
        boolean immobileDepuisLeDebut = anterieures.size() > 1
                && anterieures.stream().noneMatch(PositionGps::enMouvement)
                && anterieures.stream().allMatch(p -> dansLaZone(p, zone))
                && !anterieures.get(0).getHorodatage().isAfter(limite.plusSeconds(60));
        if (!immobileDepuisLeDebut
                || dejaAlerteDansZone(position.getCamion().getId(), zone.getId(),
                        TypeAlerteGps.STATIONNEMENT_PROLONGE, position.getHorodatage())) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(construire(position, TypeAlerteGps.STATIONNEMENT_PROLONGE, zone,
                "%s stationne dans la zone %s depuis plus de %d minutes".formatted(
                        position.getCamion().getImmatriculation(), zone.getNom(),
                        zone.getDureeMaxMinutes())));
    }

    /** Utilisation en dehors des plages horaires de service. */
    private java.util.Optional<AlerteGps> verifierHorairesDeService(PositionGps position) {
        if (!position.enMouvement()) {
            return java.util.Optional.empty();
        }
        LocalTime heure = position.getHorodatage().atZone(ZONE_LOCALE).toLocalTime();
        boolean dansLesHoraires = heure.getHour() >= heureDebutService && heure.getHour() < heureFinService;
        if (dansLesHoraires || dejaAlerte(position.getCamion().getId(), TypeAlerteGps.HORS_HORAIRES,
                position.getHorodatage())) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(construire(position, TypeAlerteGps.HORS_HORAIRES, null,
                "%s circule a %s, hors des horaires de service (%dh-%dh)".formatted(
                        position.getCamion().getImmatriculation(), heure.withNano(0),
                        heureDebutService, heureFinService)));
    }

    /** Vehicule en mouvement alors qu'aucune mission n'est ouverte pour lui. */
    private java.util.Optional<AlerteGps> verifierMouvementHorsMission(PositionGps position) {
        if (!position.enMouvement()) {
            return java.util.Optional.empty();
        }
        boolean enMission = missionRepository.missionEnCoursDuCamion(position.getCamion().getId()).isPresent();
        if (enMission || dejaAlerte(position.getCamion().getId(),
                TypeAlerteGps.MOUVEMENT_HORS_MISSION, position.getHorodatage())) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(construire(position, TypeAlerteGps.MOUVEMENT_HORS_MISSION, null,
                "%s est en mouvement sans mission en cours".formatted(
                        position.getCamion().getImmatriculation())));
    }

    private boolean dansLaZone(PositionGps position, ZoneGeofence zone) {
        return position.distanceMetres(zone.getLatitude(), zone.getLongitude()) <= zone.getRayonMetres();
    }

    /**
     * La fenetre d'anti-repetition se calcule sur l'horodatage de la position, et
     * non sur l'heure courante : sans cela, un lot de trames arrivant en differe
     * genererait autant d'alertes identiques que de positions.
     */
    private boolean dejaAlerte(Long camionId, TypeAlerteGps type, Instant horodatage) {
        return repository.existsByCamionIdAndTypeAndHorodatageAfter(
                camionId, type, horodatage.minus(ANTI_REPETITION));
    }

    private boolean dejaAlerteDansZone(Long camionId, Long zoneId, TypeAlerteGps type,
                                       Instant horodatage) {
        return repository.existsByCamionIdAndZoneIdAndTypeAndHorodatageAfter(
                camionId, zoneId, type, horodatage.minus(ANTI_REPETITION));
    }

    private AlerteGps construire(PositionGps position, TypeAlerteGps type, ZoneGeofence zone,
                                 String message) {
        AlerteGps alerte = new AlerteGps();
        alerte.setCamion(position.getCamion());
        alerte.setZone(zone);
        alerte.setType(type);
        alerte.setGravite(type.getGraviteParDefaut());
        alerte.setMessage(message);
        alerte.setLatitude(position.getLatitude());
        alerte.setLongitude(position.getLongitude());
        alerte.setVitesseKmh(position.getVitesseKmh());
        alerte.setHorodatage(position.getHorodatage());
        log.info("Alerte GPS {} : {}", type, message);
        return alerte;
    }

    /** Zone contenant ce point, s'il en existe une. Utilise pour situer un camion. */
    @Transactional(readOnly = true)
    public java.util.Optional<ZoneGeofence> zoneContenant(BigDecimal latitude, BigDecimal longitude) {
        PositionGps sonde = new PositionGps();
        sonde.setLatitude(latitude);
        sonde.setLongitude(longitude);
        return zoneRepository.findByActifTrueOrderByNomAsc().stream()
                .filter(zone -> sonde.distanceMetres(zone.getLatitude(), zone.getLongitude())
                        <= zone.getRayonMetres())
                .findFirst();
    }
}
