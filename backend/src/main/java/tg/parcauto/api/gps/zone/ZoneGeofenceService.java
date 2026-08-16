package tg.parcauto.api.gps.zone;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ZoneGeofenceService {

    private final ZoneGeofenceRepository repository;

    @Transactional(readOnly = true)
    public List<ZoneGeofenceDto.Reponse> lister(boolean actifSeulement) {
        List<ZoneGeofence> zones = actifSeulement
                ? repository.findByActifTrueOrderByNomAsc()
                : repository.findAll();
        return zones.stream().map(ZoneGeofenceDto.Reponse::de).toList();
    }

    @Transactional(readOnly = true)
    public ZoneGeofenceDto.Reponse parId(Long id) {
        return ZoneGeofenceDto.Reponse.de(charger(id));
    }

    public ZoneGeofenceDto.Reponse creer(ZoneGeofenceDto.Requete requete) {
        if (repository.existsByNomIgnoreCase(requete.nom())) {
            throw new BusinessException("Une zone porte deja le nom " + requete.nom());
        }
        ZoneGeofence zone = new ZoneGeofence();
        appliquer(zone, requete);
        return ZoneGeofenceDto.Reponse.de(repository.save(zone));
    }

    public ZoneGeofenceDto.Reponse modifier(Long id, ZoneGeofenceDto.Requete requete) {
        ZoneGeofence zone = charger(id);
        appliquer(zone, requete);
        return ZoneGeofenceDto.Reponse.de(repository.save(zone));
    }

    public void desactiver(Long id) {
        ZoneGeofence zone = charger(id);
        zone.setActif(false);
        repository.save(zone);
    }

    private ZoneGeofence charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Zone", id));
    }

    private void appliquer(ZoneGeofence z, ZoneGeofenceDto.Requete r) {
        z.setNom(r.nom().trim());
        z.setType(r.type());
        z.setLatitude(r.latitude());
        z.setLongitude(r.longitude());
        z.setRayonMetres(r.rayonMetres());
        z.setDureeMaxMinutes(r.dureeMaxMinutes());
        z.setCouleur(r.couleur());
        z.setObservations(r.observations());
        // Une zone interdite est surveillee a l'entree, sans reglage possible.
        z.setAlerteEntree(r.type().surveillanceObligatoire() || Boolean.TRUE.equals(r.alerteEntree()));
        z.setAlerteSortie(Boolean.TRUE.equals(r.alerteSortie()));
        if (r.actif() != null) {
            z.setActif(r.actif());
        }
    }
}
