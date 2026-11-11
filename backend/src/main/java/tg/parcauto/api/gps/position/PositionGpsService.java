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
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;
import tg.parcauto.api.plateforme.tenant.DiffusionEntreprise;

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
    /** Positions par envoi, au plus. */
    private static final int LOT_MAX = 1000;
    /** Horloge du traceur en avance toleree. */
    private static final Duration AVANCE_TOLEREE = Duration.ofMinutes(5);
    /** Trames rejouees acceptees jusqu'a cet age. */
    private static final Duration RETARD_MAX = Duration.ofHours(24);
    /** Compteur : saut accepte en plus de la distance faisable depuis le point precedent. */
    private static final long SAUT_COMPTEUR_KM = 50;
    private static final long VITESSE_PLAUSIBLE_KMH = 150;

    private final PositionGpsRepository repository;
    private final CamionRepository camionRepository;
    private final SurveillanceGpsService surveillance;
    private final SimpMessagingTemplate diffusion;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    /**
     * Enregistre un lot de positions normalisees, declenche la surveillance et
     * diffuse la mise a jour aux clients connectes a la carte.
     */
    @Transactional
    public List<PositionGps> enregistrer(List<PositionBrute> brutes, String fournisseur) {
        if (brutes.size() > LOT_MAX) {
            throw new BusinessException("Lot de " + brutes.size() + " positions : envoyez-en au plus " + LOT_MAX + " a la fois");
        }
        List<PositionGps> enregistrees = new ArrayList<>();
        List<Runnable> diffusions = new ArrayList<>();
        Instant maintenant = Instant.now();

        for (PositionBrute brute : brutes) {
            // Une position inutilisable est ecartee seule : elle ne fait pas perdre le reste du lot.
            if (!brute.coordonneesValides() || !brute.valeursPlausibles()) {
                log.warn("Position ignoree : coordonnees invalides pour le traceur {}", brute.deviceId());
                continue;
            }
            if (brute.deviceId() == null || brute.deviceId().isBlank()) {
                log.warn("Position ignoree : pas d'identifiant de traceur");
                continue;
            }
            Camion camion = camionRepository.findByGpsDeviceId(brute.deviceId()).orElse(null);
            if (camion == null) {
                log.warn("Position ignoree : aucun vehicule n'est associe au traceur {}", brute.deviceId());
                continue;
            }
            // Horloge du traceur dans le futur : on retient l'heure de reception ; trop ancienne : ignoree.
            Instant horodatage = brute.horodatage() == null || brute.horodatage().isAfter(maintenant.plus(AVANCE_TOLEREE))
                    ? maintenant : brute.horodatage();
            if (horodatage.isBefore(maintenant.minus(RETARD_MAX))) {
                log.warn("Position ignoree : trame du {} trop ancienne pour {}", horodatage, brute.deviceId());
                continue;
            }

            PositionGps precedente = repository
                    .findFirstByCamionIdAndHorodatageLessThanOrderByHorodatageDesc(camion.getId(), horodatage)
                    .orElse(null);

            PositionGps position = new PositionGps();
            position.setCamion(camion);
            position.setLatitude(brute.latitude());
            position.setLongitude(brute.longitude());
            position.setVitesseKmh(brute.vitesseKmh() == null || brute.vitesseKmh().signum() < 0
                    || brute.vitesseKmh().doubleValue() > 400 ? null : brute.vitesseKmh());
            position.setDirection(brute.direction() == null ? null : Math.floorMod(brute.direction(), 360));
            position.setAltitude(brute.altitude());
            position.setKilometrage(brute.kilometrage());
            position.setMoteurAllume(brute.moteurAllume());
            position.setHorodatage(horodatage);
            position.setFournisseur(fournisseur);
            position.setDeviceId(brute.deviceId());
            PositionGps sauvegardee = repository.save(position);

            // Le compteur du camion suit le traceur, si la valeur est plausible (pas de saut aberrant definitif).
            if (brute.kilometrage() != null && brute.kilometrage() > camion.getKilometrageActuel()) {
                long ecart = brute.kilometrage() - camion.getKilometrageActuel();
                long heures = precedente == null ? 24 : Math.max(1, Duration.between(precedente.getHorodatage(), horodatage).toHours() + 1);
                if (ecart <= SAUT_COMPTEUR_KM + heures * VITESSE_PLAUSIBLE_KMH
                        && tg.parcauto.api.parc.camion.CompteurKilometrique.avancerSiPlausible(camion, brute.kilometrage())) {
                    camionRepository.save(camion);
                } else {
                    log.warn("Compteur du traceur {} ignore : saut de {} km", brute.deviceId(), ecart);
                }
            }

            List<AlerteGps> alertes = surveillance.analyser(sauvegardee, precedente);
            diffusions.add(() -> diffuser(sauvegardee, alertes));
            enregistrees.add(sauvegardee);
        }
        // La carte et les alertes ne sont prevenues qu'une fois le lot reellement enregistre.
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            diffusions.forEach(Runnable::run);
                        }
                    });
        } else {
            diffusions.forEach(Runnable::run);
        }
        return enregistrees;
    }

    /** Derniere position connue de chaque camion, pour la carte du parc. */
    @Transactional(readOnly = true)
    public List<PositionGps> dernieresPositions() {
        // Deux points au meme instant : un seul marqueur par vehicule.
        java.util.Map<Long, PositionGps> parCamion = new java.util.LinkedHashMap<>();
        repository.dernieresPositions().forEach(p -> parCamion.merge(p.getCamion().getId(), p,
                (a, b) -> a.getId() >= b.getId() ? a : b));
        return new ArrayList<>(parCamion.values());
    }

    @Transactional(readOnly = true)
    public Optional<PositionGps> dernierePosition(Long camionId) {
        return repository.findFirstByCamionIdOrderByHorodatageDesc(camionId);
    }

    @Transactional(readOnly = true)
    public List<PositionGps> trajet(Long camionId, Instant debut, Instant fin) {
        // Au plus POINTS_TRAJET_MAX points en memoire : au-dela, un point sur n.
        long total = repository.compterTrajet(camionId, debut, fin);
        if (total <= POINTS_TRAJET_MAX) {
            return repository.trajet(camionId, debut, fin);
        }
        long pas = (total + POINTS_TRAJET_MAX - 1) / POINTS_TRAJET_MAX;
        return repository.trajetEchantillonne(camionId, debut, fin, pas);
    }

    private static final long POINTS_TRAJET_MAX = 10_000;

    /** Conservation des positions : 12 mois. Purge chaque nuit, par paquets, toutes entreprises. */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 30 3 * * *")
    public void purgerAnciennesPositions() {
        java.sql.Timestamp limite = java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(365)));
        int supprimees;
        do {
            supprimees = jdbc.update("""
                    delete from position_gps where id in (
                        select id from position_gps where horodatage < ? limit 10000)
                    """, limite);
        } while (supprimees > 0);
    }

    @Transactional(readOnly = true)
    public List<PositionGps> dernieres(Long camionId, int nombre) {
        return repository.dernieres(camionId, PageRequest.of(0, Math.max(1, Math.min(nombre, 500))));
    }

    /** Vrai si le traceur n'a plus donne signe de vie depuis le seuil admis. */
    public boolean horsConnexion(PositionGps position) {
        return position == null
                || position.getHorodatage().isBefore(Instant.now().minus(SEUIL_DECONNEXION));
    }

    private void diffuser(PositionGps position, List<AlerteGps> alertes) {
        try {
            String canal = DiffusionEntreprise.canal(ContexteEntreprise.exiger());
            diffusion.convertAndSend(canal + "/gps/positions", PositionGpsDto.Reponse.de(position));
            alertes.forEach(alerte ->
                    diffusion.convertAndSend(canal + "/gps/alertes", alerte.getMessage()));
        } catch (RuntimeException e) {
            // Une diffusion temps reel en echec ne doit jamais perdre la position enregistree.
            log.warn("Diffusion temps reel impossible : {}", e.getMessage());
        }
    }
}
