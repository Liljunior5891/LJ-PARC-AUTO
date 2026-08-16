package tg.parcauto.api.gps.alerte;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.ResourceNotFoundException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AlerteGpsService {

    private final AlerteGpsRepository repository;

    @Transactional(readOnly = true)
    public Page<AlerteGpsDto.Reponse> rechercher(Boolean traitee, Pageable pageable) {
        Page<AlerteGps> page = traitee == null
                ? repository.toutes(pageable)
                : repository.parEtat(traitee, pageable);
        return page.map(AlerteGpsDto.Reponse::de);
    }

    @Transactional(readOnly = true)
    public List<AlerteGpsDto.Reponse> dernieresNonTraitees(int nombre) {
        return repository.nonTraitees(PageRequest.of(0, nombre)).stream()
                .map(AlerteGpsDto.Reponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public AlerteGpsDto.Synthese synthese() {
        return new AlerteGpsDto.Synthese(
                repository.countByTraiteeFalse(),
                repository.countByTypeAndTraiteeFalse(TypeAlerteGps.EXCES_VITESSE),
                repository.countByTypeAndTraiteeFalse(TypeAlerteGps.SORTIE_ZONE),
                repository.countByTypeAndTraiteeFalse(TypeAlerteGps.ZONE_INTERDITE),
                repository.countByTypeAndTraiteeFalse(TypeAlerteGps.GPS_DECONNECTE));
    }

    /** Marque l'alerte comme traitee et trace l'operateur. */
    public AlerteGpsDto.Reponse traiter(Long id) {
        AlerteGps alerte = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Alerte GPS", id));
        alerte.setTraitee(true);
        alerte.setTraiteeLe(Instant.now());
        var authentification = SecurityContextHolder.getContext().getAuthentication();
        alerte.setTraiteePar(authentification == null ? "systeme" : authentification.getName());
        return AlerteGpsDto.Reponse.de(repository.save(alerte));
    }
}
