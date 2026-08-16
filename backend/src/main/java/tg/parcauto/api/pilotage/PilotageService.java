package tg.parcauto.api.pilotage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.exploitation.mission.MissionRepository;
import tg.parcauto.api.finance.depense.CarburantService;
import tg.parcauto.api.finance.depense.CategorieDepense;
import tg.parcauto.api.finance.depense.DepenseDto;
import tg.parcauto.api.finance.depense.DepenseService;
import tg.parcauto.api.finance.rentabilite.RentabiliteDto;
import tg.parcauto.api.finance.rentabilite.RentabiliteService;
import tg.parcauto.api.gps.alerte.AlerteGpsService;
import tg.parcauto.api.gps.position.PositionGpsService;
import tg.parcauto.api.maintenance.entretien.EntretienDto;
import tg.parcauto.api.maintenance.entretien.EntretienService;
import tg.parcauto.api.maintenance.incident.IncidentRepository;
import tg.parcauto.api.maintenance.incident.StatutIncident;
import tg.parcauto.api.maintenance.pneu.PneuService;
import tg.parcauto.api.parc.camion.Camion;
import tg.parcauto.api.parc.camion.CamionService;
import tg.parcauto.api.parc.camion.StatutCamion;
import tg.parcauto.api.parc.document.DocumentDto;
import tg.parcauto.api.parc.document.DocumentService;
import tg.parcauto.api.parc.document.ProprietaireDocument;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Pilotage de la direction : KPI du chapitre 41 et fiche synthetique du
 * chapitre 36. Ce service n'a pas de donnees propres : il assemble celles des
 * modules parc, exploitation, finances, GPS et maintenance.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PilotageService {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private final CamionService camionService;
    private final RentabiliteService rentabiliteService;
    private final DepenseService depenseService;
    private final CarburantService carburantService;
    private final MissionRepository missionRepository;
    private final EntretienService entretienService;
    private final tg.parcauto.api.maintenance.entretien.EntretienRepository entretienRepository;
    private final PneuService pneuService;
    private final IncidentRepository incidentRepository;
    private final DocumentService documentService;
    private final PositionGpsService positionService;
    private final AlerteGpsService alerteService;
    private final TableauBordService tableauBordService;

    /** Les indicateurs de direction du chapitre 41. */
    public KpiDto kpi(LocalDate debut, LocalDate fin) {
        RentabiliteDto.Global rentabilite = rentabiliteService.global(debut, fin);
        Map<StatutCamion, Long> repartition = camionService.repartitionParStatut();
        long total = repartition.values().stream().mapToLong(Long::longValue).sum();
        long disponibles = repartition.getOrDefault(StatutCamion.DISPONIBLE, 0L);
        long immobilises = repartition.getOrDefault(StatutCamion.IMMOBILISE, 0L)
                + repartition.getOrDefault(StatutCamion.EN_MAINTENANCE, 0L)
                + repartition.getOrDefault(StatutCamion.ACCIDENTE, 0L);

        long kilometres = rentabilite.camions().stream()
                .mapToLong(RentabiliteDto.LigneCamion::distanceKm).sum();
        long missions = rentabilite.camions().stream()
                .mapToLong(RentabiliteDto.LigneCamion::nombreMissions).sum();

        // Le taux d'utilisation moyen est celui des fiches performance, pondere par camion.
        BigDecimal tauxUtilisation = total == 0 ? BigDecimal.ZERO
                : moyenne(rentabilite.camions().stream()
                        .map(ligne -> rentabiliteService.ficheCamion(ligne.camionId(), debut, fin)
                                .utilisation().tauxUtilisationPourcent())
                        .toList());

        BigDecimal coutMaintenance = depenseService.repartitionParCategorie(debut, fin, null).stream()
                .filter(l -> l.categorie() == CategorieDepense.ENTRETIEN
                        || l.categorie() == CategorieDepense.PNEUS)
                .map(DepenseDto.LigneCategorie::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal consommation = moyenne(rentabilite.camions().stream()
                .map(ligne -> carburantService.synthese(ligne.camionId(), debut, fin)
                        .consommationMoyenneL100km())
                .filter(java.util.Objects::nonNull)
                .toList());

        return new KpiDto(
                debut, fin,
                total, total - immobilises,
                tauxUtilisation,
                total == 0 ? BigDecimal.ZERO
                        : BigDecimal.valueOf(disponibles).multiply(CENT)
                                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP),
                joursImmobilisation(debut, fin),
                rentabilite.totalRecettes(), rentabilite.totalDepenses(), rentabilite.resultat(),
                rentabilite.margePourcent(),
                rentabilite.recetteMoyenneParCamion(), rentabilite.depenseMoyenneParCamion(),
                parKm(rentabilite.totalDepenses(), kilometres),
                parKm(rentabilite.totalRecettes(), kilometres),
                coutMaintenance,
                kilometres, missions, consommation);
    }

    /** Fiche synthetique d'un camion, chapitre 36. */
    public FicheSyntheseDto ficheSynthese(Long camionId, LocalDate debut, LocalDate fin) {
        Camion camion = camionService.charger(camionId);
        RentabiliteDto.FicheCamion performance = rentabiliteService.ficheCamion(camionId, debut, fin);
        var carburant = carburantService.synthese(camionId, debut, fin);

        var ligneCarte = tableauBordService.vueParc().stream()
                .filter(l -> l.camionId().equals(camionId))
                .findFirst()
                .orElse(null);
        var position = positionService.dernierePosition(camionId).orElse(null);

        List<EntretienDto.Reponse> entretiens = entretienService.historiqueDuCamion(camionId);
        long aVenir = entretiens.stream()
                .filter(e -> e.statut() == tg.parcauto.api.maintenance.entretien.StatutEntretien.PLANIFIE)
                .count();
        long enRetard = entretiens.stream().filter(EntretienDto.Reponse::enRetard).count();
        long pneus = pneuService.duCamion(camionId).stream()
                .filter(tg.parcauto.api.maintenance.pneu.PneuDto.Reponse::aRemplacer)
                .count();
        long incidents = incidentRepository.countByCamionIdAndStatutIn(camionId,
                List.of(StatutIncident.DECLARE, StatutIncident.PRIS_EN_CHARGE,
                        StatutIncident.EN_REPARATION));
        long documents = documentService.aExpirer(30).stream()
                .filter(d -> d.proprietaireType() == ProprietaireDocument.CAMION
                        && camionId.equals(d.proprietaireId()))
                .count();
        long alertesGps = alerteService.dernieresNonTraitees(100).stream()
                .filter(a -> camionId.equals(a.camionId()))
                .count();

        String marqueModele = ((camion.getMarque() == null ? "" : camion.getMarque()) + " "
                + (camion.getModele() == null ? "" : camion.getModele())).trim();

        return new FicheSyntheseDto(
                camion.getId(), camion.getNumeroInterne(), camion.getImmatriculation(),
                marqueModele.isEmpty() ? null : marqueModele,
                camion.getStatut(), camion.getStatut().getLibelle(), debut, fin,
                ligneCarte == null ? null : ligneCarte.chauffeurNom(),
                ligneCarte == null ? null : ligneCarte.clientNom(),
                ligneCarte == null ? null : ligneCarte.activite(),
                ligneCarte == null ? null : ligneCarte.localisation(),
                position == null ? null : position.getHorodatage(),
                camion.getKilometrageActuel(),
                performance.utilisation().distanceKm(),
                performance.utilisation().nombreMissions(),
                performance.totalRecettes(), performance.totalDepenses(), performance.resultat(),
                performance.margePourcent(), performance.coutParKm(), performance.revenuParKm(),
                performance.depensesParCategorie(),
                carburant.consommationMoyenneL100km(), carburant.coutCarburantParKm(),
                aVenir, enRetard, pneus, incidents, documents, alertesGps);
    }

    /**
     * Jours d'immobilisation cumules sur la periode, deduits des interventions
     * effectivement realisees (chapitres 7 et 41).
     */
    private long joursImmobilisation(LocalDate debut, LocalDate fin) {
        return entretienRepository.joursImmobilisationSurPeriode(debut, fin);
    }

    private BigDecimal moyenne(List<BigDecimal> valeurs) {
        if (valeurs.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return valeurs.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(valeurs.size()), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal parKm(BigDecimal montant, long km) {
        return km <= 0 ? BigDecimal.ZERO
                : montant.divide(BigDecimal.valueOf(km), 2, RoundingMode.HALF_UP);
    }
}
