package tg.parcauto.api.maintenance.incident;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;
import tg.parcauto.api.plateforme.tenant.DiffusionEntreprise;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.exploitation.mission.MissionService;
import tg.parcauto.api.maintenance.entretien.EntretienDto;
import tg.parcauto.api.maintenance.entretien.EntretienService;
import tg.parcauto.api.maintenance.entretien.TypeEntretien;
import tg.parcauto.api.parc.camion.Camion;
import tg.parcauto.api.parc.camion.CamionRepository;
import tg.parcauto.api.parc.camion.CamionService;
import tg.parcauto.api.parc.camion.StatutCamion;
import tg.parcauto.api.parc.chauffeur.ChauffeurService;

import java.time.Instant;
import java.util.List;

/**
 * Incidents declares depuis le terrain, chapitre 33 du cahier des charges.
 * <p>
 * Un incident immobilisant place immediatement le camion en immobilisation :
 * il cesse d'apparaitre comme affectable tant qu'il n'est pas resolu.
 * Le responsable est notifie en temps reel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class IncidentService {

    private final IncidentRepository repository;
    private final CamionService camionService;
    private final CamionRepository camionRepository;
    private final ChauffeurService chauffeurService;
    private final MissionService missionService;
    private final EntretienService entretienService;
    private final NumerotationService numerotation;
    private final SimpMessagingTemplate diffusion;

    @Transactional(readOnly = true)
    public Page<IncidentDto.Reponse> lister(Pageable pageable) {
        return repository.tous(pageable).map(IncidentDto.Reponse::de);
    }

    @Transactional(readOnly = true)
    public List<IncidentDto.Reponse> ouverts() {
        return repository.ouverts().stream().map(IncidentDto.Reponse::de).toList();
    }

    @Transactional(readOnly = true)
    public IncidentDto.Reponse parId(Long id) {
        return IncidentDto.Reponse.de(charger(id));
    }

    /** Declare un incident et notifie le responsable. */
    public IncidentDto.Reponse declarer(IncidentDto.Requete requete) {
        Camion camion = camionService.charger(requete.camionId());

        Incident incident = new Incident();
        incident.setNumero(numerotation.prochainNumero("INC"));
        incident.setCamion(camion);
        incident.setChauffeur(requete.chauffeurId() == null
                ? null : chauffeurService.charger(requete.chauffeurId()));
        incident.setMission(requete.missionId() == null
                ? null : missionService.charger(requete.missionId()));
        incident.setType(requete.type());
        incident.setGravite(requete.gravite() == null ? GraviteIncident.MOYENNE : requete.gravite());
        incident.setDescription(requete.description().trim());
        incident.setLieu(requete.lieu());
        incident.setLatitude(requete.latitude());
        incident.setLongitude(requete.longitude());
        incident.setPhotoUrl(requete.photoUrl());
        incident.setSurvenuLe(requete.survenuLe() == null ? Instant.now() : requete.survenuLe());
        incident.setCoutEstime(requete.coutEstime());
        incident.setImmobilisant(requete.immobilisant() == null
                ? requete.type().isImmobilisantParDefaut() : requete.immobilisant());

        if (incident.isImmobilisant() && camion.getStatut().exploitable()) {
            camion.setStatut(requete.type() == TypeIncident.ACCIDENT
                    ? StatutCamion.ACCIDENTE : StatutCamion.IMMOBILISE);
            camionRepository.save(camion);
        }

        Incident enregistre = repository.save(incident);
        notifier(enregistre);
        return IncidentDto.Reponse.de(enregistre);
    }

    /** Fait avancer le traitement et libere le camion lorsque l'incident est resolu. */
    public IncidentDto.Reponse traiter(Long id, IncidentDto.Traitement traitement) {
        Incident incident = charger(id);
        if (incident.getStatut() == StatutIncident.CLOS) {
            throw new BusinessException("Cet incident est clos");
        }

        incident.setStatut(traitement.statut());
        if (traitement.resolution() != null && !traitement.resolution().isBlank()) {
            incident.setResolution(traitement.resolution());
        }
        if (traitement.coutEstime() != null) {
            incident.setCoutEstime(traitement.coutEstime());
        }
        if (traitement.statut().clos()) {
            incident.setResoluLe(Instant.now());
            libererCamion(incident);
        }
        if (Boolean.TRUE.equals(traitement.ouvrirEntretien()) && incident.getEntretien() == null) {
            ouvrirEntretien(incident);
        }
        return IncidentDto.Reponse.de(repository.save(incident));
    }

    @Transactional(readOnly = true)
    public Incident charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Incident", id));
    }

    /** Le camion redevient disponible s'il n'est pas retenu par un autre incident ouvert. */
    private void libererCamion(Incident incident) {
        Camion camion = incident.getCamion();
        boolean immobilisePar = camion.getStatut() == StatutCamion.IMMOBILISE
                || camion.getStatut() == StatutCamion.ACCIDENTE;
        if (!incident.isImmobilisant() || !immobilisePar) {
            return;
        }
        long autresOuverts = repository.countByCamionIdAndStatutIn(camion.getId(),
                List.of(StatutIncident.DECLARE, StatutIncident.PRIS_EN_CHARGE,
                        StatutIncident.EN_REPARATION)) - 1;
        if (autresOuverts <= 0) {
            camion.setStatut(StatutCamion.DISPONIBLE);
            camionRepository.save(camion);
        }
    }

    /** Ouvre une intervention de reparation rattachee a l'incident. */
    private void ouvrirEntretien(Incident incident) {
        var requete = new EntretienDto.Requete(
                incident.getCamion().getId(),
                incident.getType() == TypeIncident.ACCIDENT
                        ? TypeEntretien.CARROSSERIE : TypeEntretien.REPARATION,
                "Suite a l'incident " + incident.getNumero() + " — " + incident.getType().getLibelle(),
                java.time.LocalDate.now(), null, null, null, null, null,
                incident.getDescription());
        var entretien = entretienService.planifier(requete);
        incident.setEntretien(entretienService.charger(entretien.id()));
        incident.setStatut(StatutIncident.EN_REPARATION);
    }

    private void notifier(Incident incident) {
        try {
            diffusion.convertAndSend(DiffusionEntreprise.canal(ContexteEntreprise.exiger()) + "/incidents",
                    IncidentDto.Reponse.de(incident));
        } catch (RuntimeException e) {
            // La notification ne doit jamais faire echouer la declaration de l'incident.
            log.warn("Notification d'incident impossible : {}", e.getMessage());
        }
        log.info("Incident {} declare sur {} : {}", incident.getNumero(),
                incident.getCamion().getImmatriculation(), incident.getType().getLibelle());
    }
}
