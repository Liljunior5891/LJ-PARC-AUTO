package tg.parcauto.api.gps.alerte;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tg.parcauto.api.gps.position.PositionGps;
import tg.parcauto.api.gps.position.PositionGpsRepository;
import tg.parcauto.api.parc.camion.Camion;
import tg.parcauto.api.parc.camion.CamionRepository;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Traceur muet : un vehicule equipe qui n'a plus envoye de position depuis
 * deux heures declenche l'alerte « GPS deconnecte » (boitier debranche,
 * vehicule vole et traceur coupe). Une seule alerte par silence.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VeilleTraceurs {

    private static final Duration SILENCE = Duration.ofHours(2);

    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    private final CamionRepository camions;
    private final PositionGpsRepository positions;
    private final AlerteGpsRepository alertes;

    @Scheduled(fixedDelay = 900_000, initialDelay = 120_000)
    public void veiller() {
        List<Long> entreprises = jdbc.queryForList(
                "select distinct entreprise_id from camion where gps_device_id is not null and actif", Long.class);
        TransactionTemplate tx = new TransactionTemplate(transactions);
        for (Long entreprise : entreprises) {
            try {
                ContexteEntreprise.definir(entreprise);
                tx.executeWithoutResult(s -> veillerEntreprise());
            } catch (RuntimeException e) {
                log.warn("Veille des traceurs impossible pour l'entreprise {} : {}", entreprise, e.getMessage());
            } finally {
                ContexteEntreprise.effacer();
            }
        }
    }

    private void veillerEntreprise() {
        Instant limite = Instant.now().minus(SILENCE);
        for (Camion camion : camions.findAll()) {
            if (!camion.isActif() || camion.getGpsDeviceId() == null || camion.getGpsDeviceId().isBlank()) {
                continue;
            }
            PositionGps derniere = positions.findFirstByCamionIdOrderByHorodatageDesc(camion.getId()).orElse(null);
            if (derniere == null || derniere.getHorodatage().isAfter(limite)) {
                continue;
            }
            if (alertes.existsByCamionIdAndTypeAndHorodatageAfter(camion.getId(), TypeAlerteGps.GPS_DECONNECTE,
                    derniere.getHorodatage())) {
                continue;
            }
            AlerteGps alerte = new AlerteGps();
            alerte.setCamion(camion);
            alerte.setType(TypeAlerteGps.GPS_DECONNECTE);
            alerte.setGravite(TypeAlerteGps.GPS_DECONNECTE.getGraviteParDefaut());
            alerte.setMessage("%s : aucun signal GPS depuis %s".formatted(camion.getImmatriculation(),
                    derniere.getHorodatage()));
            alerte.setLatitude(derniere.getLatitude());
            alerte.setLongitude(derniere.getLongitude());
            alerte.setHorodatage(Instant.now());
            alertes.save(alerte);
        }
    }
}
