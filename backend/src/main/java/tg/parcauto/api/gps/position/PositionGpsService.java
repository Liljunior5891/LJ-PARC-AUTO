package tg.parcauto.api.gps.position;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.gps.alerte.AlerteGps;
import tg.parcauto.api.gps.alerte.SurveillanceGpsService;
import tg.parcauto.api.gps.integration.PositionBrute;
import tg.parcauto.api.parc.camion.Camion;
import tg.parcauto.api.parc.camion.CamionRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reception et exploitation des positions GPS, chapitres 22 a 24.
 * <p>
 * Le rattachement au camion se fait par l'identifiant du traceur enregistre sur
 * sa fiche ({@code gpsDeviceId}) : une position dont le traceur est inconnu est
 * rejetee plutot que rattachee au hasard.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionGpsService {

    /** Au-dela de ce delai sans trame, le traceur est considere hors connexion. */
    private static final Duration SEUIL_DECONNEXION = Duration.ofHours(2);

    private final PositionGpsRepository repository;
    private final CamionRepository camionRepository;
    private final SurveillanceGpsService surveillance;
    private final SimpMessagingTemplate diffusion;

    /**
     * Enregistre un lot de positions normalisees, declenche la surveillance et
     * diffuse la mise a jour aux clients connectes a la carte.
     */
    @Transactional
    public List<PositionGps> enregistrer(List<PositionBrute> brutes, String fournisseur) {
        List<PositionGps> enregistrees = new ArrayList<>();

        for (PositionBrute brute : brutes) {
            if (!brute.coordonneesValides()) {
                log.warn("Position ignoree : coordonnees invalides pour le traceur {}", brute.deviceId());
                continue;
            }
            if (brute.deviceId() == null || brute.deviceId().isBlank()) {
                throw new BusinessException("Position sans identifiant de traceur");
            }
            Camion camion = camionRepository.findByGpsDeviceId(brute.deviceId())
                    .orElseThrow(() -> new BusinessException(
                            "Aucun vehicule n'est associe au traceur " + brute.deviceId()));

            PositionGps precedente = repository
                    .findFirstByCamionIdOrderByHorodatageDesc(camion.getId())
                    .orElse(null);

            PositionGps position = new PositionGps();
            position.setCamion(camion);
            position.setLatitude(brute.latitude());
            position.setLongitude(brute.longitude());
            position.setVitesseKmh(brute.vitesseKmh());
            position.setDirection(brute.direction());
            position.setAltitude(brute.altitude());
            position.setKilometrage(brute.kilometrage());
            position.setMoteurAllume(brute.moteurAllume());
            position.setHorodatage(brute.horodatage() == null ? Instant.now() : brute.horodatage());
            position.setFournisseur(fournisseur);
            position.setDeviceId(brute.deviceId());
            PositionGps sauvegardee = repository.save(position);

            // Le compteur du camion suit le traceur lorsque celui-ci le transmet.
            if (brute.kilometrage() != null && brute.kilometrage() > camion.getKilometrageActuel()) {
                camion.setKilometrageActuel(brute.kilometrage());
                camionRepository.save(camion);
            }

            List<AlerteGps> alertes = surveillance.analyser(sauvegardee, precedente);
            diffuser(sauvegardee, alertes);
            enregistrees.add(sauvegardee);
        }
        return enregistrees;
    }

    /** Derniere position connue de chaque camion, pour la carte du parc. */
    @Transactional(readOnly = true)
    public List<PositionGps> dernieresPositions() {
        return repository.dernieresPositions();
    }

    @Transactional(readOnly = true)
    public Optional<PositionGps> dernierePosition(Long camionId) {
        return repository.findFirstByCamionIdOrderByHorodatageDesc(camionId);
    }

    @Transactional(readOnly = true)
    public List<PositionGps> trajet(Long camionId, Instant debut, Instant fin) {
        return repository.trajet(camionId, debut, fin);
    }

    @Transactional(readOnly = true)
    public List<PositionGps> dernieres(Long camionId, int nombre) {
        return repository.dernieres(camionId, PageRequest.of(0, nombre));
    }

    /** Vrai si le traceur n'a plus donne signe de vie depuis le seuil admis. */
    public boolean horsConnexion(PositionGps position) {
        return position == null
                || position.getHorodatage().isBefore(Instant.now().minus(SEUIL_DECONNEXION));
    }

    private void diffuser(PositionGps position, List<AlerteGps> alertes) {
        try {
            diffusion.convertAndSend("/topic/gps/positions", PositionGpsDto.Reponse.de(position));
            alertes.forEach(alerte ->
                    diffusion.convertAndSend("/topic/gps/alertes", alerte.getMessage()));
        } catch (RuntimeException e) {
            // Une diffusion temps reel en echec ne doit jamais perdre la position enregistree.
            log.warn("Diffusion temps reel impossible : {}", e.getMessage());
        }
    }
}
