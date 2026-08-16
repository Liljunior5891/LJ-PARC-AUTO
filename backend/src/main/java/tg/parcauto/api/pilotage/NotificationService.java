package tg.parcauto.api.pilotage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.exploitation.location.LocationService;
import tg.parcauto.api.finance.facture.FactureService;
import tg.parcauto.api.gps.alerte.AlerteGpsService;
import tg.parcauto.api.maintenance.entretien.EntretienService;
import tg.parcauto.api.maintenance.incident.IncidentService;
import tg.parcauto.api.maintenance.pneu.PneuService;
import tg.parcauto.api.parc.chauffeur.ChauffeurService;
import tg.parcauto.api.parc.document.DocumentService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Centre de notifications, chapitre 34 du cahier des charges.
 * <p>
 * Ce service ne stocke rien : il interroge chaque module et presente une liste
 * unique et ordonnee de ce qui reclame une action. Une notification a toujours
 * une cible cliquable, sans quoi elle serait inutilisable.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final DocumentService documentService;
    private final ChauffeurService chauffeurService;
    private final EntretienService entretienService;
    private final PneuService pneuService;
    private final IncidentService incidentService;
    private final LocationService locationService;
    private final FactureService factureService;
    private final AlerteGpsService alerteGpsService;

    public List<NotificationDto> notifications(int joursAlerte) {
        List<NotificationDto> notifications = new ArrayList<>();

        // --- Documents et permis (chapitres 21 et 8) ---
        documentService.aExpirer(joursAlerte).forEach(document -> notifications.add(new NotificationDto(
                document.expire() ? NotificationDto.Niveau.CRITIQUE : NotificationDto.Niveau.AVERTISSEMENT,
                "Documents",
                document.expire()
                        ? "%s expire depuis %d jours (%s)".formatted(document.libelle(),
                                Math.abs(document.joursAvantExpiration()), document.proprietaireLibelle())
                        : "%s expire dans %d jours (%s)".formatted(document.libelle(),
                                document.joursAvantExpiration(), document.proprietaireLibelle()),
                "/documents", document.id(), null)));

        chauffeurService.permisAExpirer(joursAlerte).forEach(chauffeur -> notifications.add(
                new NotificationDto(NotificationDto.Niveau.AVERTISSEMENT, "Chauffeurs",
                        "Permis de %s a renouveler avant le %s".formatted(
                                chauffeur.nomComplet(), chauffeur.permisExpiration()),
                        "/chauffeurs", chauffeur.id(), null)));

        // --- Maintenance (chapitres 19, 20 et 33) ---
        entretienService.alertes().forEach(entretien -> notifications.add(new NotificationDto(
                entretien.enRetard() ? NotificationDto.Niveau.CRITIQUE : NotificationDto.Niveau.AVERTISSEMENT,
                "Maintenance",
                "%s : %s sur %s".formatted(entretien.numero(), entretien.libelle(),
                        entretien.camionImmatriculation()),
                "/maintenance", entretien.id(), null)));

        pneuService.aRemplacer().forEach(pneu -> notifications.add(new NotificationDto(
                NotificationDto.Niveau.AVERTISSEMENT, "Pneumatiques",
                "Pneu %s a remplacer sur %s (usure %d %%)".formatted(
                        pneu.reference(), pneu.camionImmatriculation(),
                        pneu.usurePourcent() == null ? 100 : pneu.usurePourcent()),
                "/maintenance", pneu.id(), null)));

        incidentService.ouverts().forEach(incident -> notifications.add(new NotificationDto(
                incident.immobilisant() ? NotificationDto.Niveau.CRITIQUE : NotificationDto.Niveau.INFORMATION,
                "Incidents",
                "%s : %s sur %s".formatted(incident.numero(), incident.typeLibelle(),
                        incident.camionImmatriculation()),
                "/maintenance", incident.id(), incident.survenuLe())));

        // --- Exploitation et finances (chapitres 11 et 28) ---
        locationService.echeances(joursAlerte).forEach(contrat -> notifications.add(new NotificationDto(
                contrat.echue() ? NotificationDto.Niveau.CRITIQUE : NotificationDto.Niveau.AVERTISSEMENT,
                "Contrats",
                "%s (%s) arrive a echeance le %s".formatted(
                        contrat.numero(), contrat.clientNom(), contrat.dateFin()),
                "/locations", contrat.id(), null)));

        factureService.impayees().forEach(facture -> notifications.add(new NotificationDto(
                facture.enRetard() ? NotificationDto.Niveau.CRITIQUE : NotificationDto.Niveau.INFORMATION,
                "Facturation",
                "%s : %s reste du par %s".formatted(facture.numero(),
                        facture.resteAPayer().toPlainString(), facture.clientNom()),
                "/facturation", facture.id(), null)));

        // --- Geolocalisation (chapitre 26) ---
        alerteGpsService.dernieresNonTraitees(20).forEach(alerte -> notifications.add(new NotificationDto(
                alerte.gravite() == tg.parcauto.api.gps.alerte.GraviteAlerte.CRITIQUE
                        ? NotificationDto.Niveau.CRITIQUE : NotificationDto.Niveau.AVERTISSEMENT,
                "GPS", alerte.message(), "/gps", alerte.id(), alerte.horodatage())));

        // Les alertes critiques d'abord, puis les plus recentes.
        notifications.sort(Comparator
                .comparing(NotificationDto::niveau)
                .thenComparing(NotificationDto::horodatage,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        return notifications;
    }

    /** Compteurs par niveau, pour la pastille du menu. */
    public NotificationDto.Synthese synthese(int joursAlerte) {
        List<NotificationDto> notifications = notifications(joursAlerte);
        return new NotificationDto.Synthese(
                notifications.size(),
                notifications.stream().filter(n -> n.niveau() == NotificationDto.Niveau.CRITIQUE).count(),
                notifications.stream()
                        .filter(n -> n.niveau() == NotificationDto.Niveau.AVERTISSEMENT).count(),
                Instant.now());
    }
}
