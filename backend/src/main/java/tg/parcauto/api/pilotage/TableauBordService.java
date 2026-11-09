package tg.parcauto.api.pilotage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.exploitation.location.Location;
import tg.parcauto.api.exploitation.location.LocationRepository;
import tg.parcauto.api.exploitation.mission.Mission;
import tg.parcauto.api.exploitation.mission.MissionRepository;
import tg.parcauto.api.finance.facture.FactureService;
import tg.parcauto.api.finance.facture.PaiementRepository;
import tg.parcauto.api.finance.rentabilite.RentabiliteService;
import tg.parcauto.api.parc.affectation.Affectation;
import tg.parcauto.api.parc.affectation.AffectationRepository;
import tg.parcauto.api.parc.camion.Camion;
import tg.parcauto.api.parc.camion.MarqueCamion;
import tg.parcauto.api.parc.camion.CamionRepository;
import tg.parcauto.api.parc.camion.CamionService;
import tg.parcauto.api.parc.camion.StatutCamion;
import tg.parcauto.api.parc.chauffeur.ChauffeurRepository;
import tg.parcauto.api.parc.chauffeur.ChauffeurService;
import tg.parcauto.api.parc.chauffeur.StatutChauffeur;
import tg.parcauto.api.parc.document.DocumentDto;
import tg.parcauto.api.parc.document.DocumentService;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TableauBordService {

    private final tg.parcauto.api.finance.recette.RecetteRepository recettesEnLigne;

    private final CamionService camionService;
    private final CamionRepository camionRepository;
    private final ChauffeurService chauffeurService;
    private final ChauffeurRepository chauffeurRepository;
    private final AffectationRepository affectationRepository;
    private final DocumentService documentService;
    private final LocationRepository locationRepository;
    private final MissionRepository missionRepository;
    private final RentabiliteService rentabiliteService;
    private final tg.parcauto.api.finance.depense.DepenseService depenseService;
    private final FactureService factureService;
    private final PaiementRepository paiementRepository;

    public TableauBordDto.Parc parc(int joursAlerte) {
        Map<StatutCamion, Long> repartition = camionService.repartitionParStatut();
        long total = repartition.values().stream().mapToLong(Long::longValue).sum();
        long affectationsEnCours = affectationRepository.countByDateFinIsNull();

        List<DocumentDto.Reponse> documents = documentService.aExpirer(joursAlerte);
        long expires = documents.stream().filter(DocumentDto.Reponse::expire).count();

        return new TableauBordDto.Parc(
                total,
                repartition.getOrDefault(StatutCamion.DISPONIBLE, 0L),
                repartition.getOrDefault(StatutCamion.EN_LOCATION, 0L),
                repartition.getOrDefault(StatutCamion.EN_MISSION, 0L),
                repartition.getOrDefault(StatutCamion.EN_MAINTENANCE, 0L),
                repartition.getOrDefault(StatutCamion.IMMOBILISE, 0L),
                repartition,
                chauffeurRepository.countByStatut(StatutChauffeur.ACTIF),
                affectationsEnCours,
                Math.max(0, total - affectationsEnCours),
                documents.size() - expires,
                expires,
                chauffeurService.permisAExpirer(joursAlerte).size());
    }

    /** Indicateurs financiers du chapitre 4.2, sur la periode demandee. */
    public TableauBordDto.Finances finances(LocalDate debut, LocalDate fin) {
        var rentabilite = rentabiliteService.global(debut, fin);
        var creances = factureService.creances();
        java.math.BigDecimal enLigne = recettesEnLigne.totalEnLigneSurPeriode(debut, fin);
        return new TableauBordDto.Finances(
                debut, fin,
                rentabilite.totalRecettes(),
                rentabilite.totalDepenses(),
                rentabilite.resultat(),
                rentabilite.margePourcent(),
                paiementRepository.totalEncaisseSurPeriode(debut, fin),
                creances.montantRestant(),
                creances.nombreFacturesImpayees(),
                creances.nombreEnRetard(),
                rentabilite.recetteMoyenneParCamion(),
                rentabilite.depenseMoyenneParCamion(),
                enLigne, rentabilite.totalRecettes().subtract(enLigne),
                rentabilite.chargesGenerales(), rentabilite.resultatNet(), depenseService.nombreAValider());
    }

    /**
     * Vue globale du parc (chapitre 5) : pour chaque camion, son chauffeur, son
     * activite en cours et le client concerne.
     */
    public List<VueParcDto> vueParc() {
        Map<Long, Affectation> affectations = affectationRepository.affectationsEnCours().stream()
                .collect(Collectors.toMap(a -> a.getCamion().getId(), Function.identity(), (a, b) -> a));
        Map<Long, Mission> missions = missionRepository.missionsEnCours().stream()
                .collect(Collectors.toMap(m -> m.getCamion().getId(), Function.identity(), (a, b) -> a));
        Map<Long, Location> locations = locationRepository.locationsActives().stream()
                .collect(Collectors.toMap(l -> l.getCamion().getId(), Function.identity(), (a, b) -> a));

        return camionRepository.findAll().stream()
                .filter(Camion::isActif)
                .sorted(Comparator.comparing(Camion::getImmatriculation))
                .map(camion -> ligne(camion, affectations.get(camion.getId()),
                        missions.get(camion.getId()), locations.get(camion.getId())))
                .toList();
    }

    private VueParcDto ligne(Camion camion, Affectation affectation, Mission mission, Location location) {
        String chauffeurNom = null;
        Long chauffeurId = null;
        if (mission != null && mission.getChauffeur() != null) {
            chauffeurId = mission.getChauffeur().getId();
            chauffeurNom = mission.getChauffeur().nomComplet();
        } else if (affectation != null) {
            chauffeurId = affectation.getChauffeur().getId();
            chauffeurNom = affectation.getChauffeur().nomComplet();
        } else if (location != null && location.getChauffeur() != null) {
            chauffeurId = location.getChauffeur().getId();
            chauffeurNom = location.getChauffeur().nomComplet();
        }

        String clientNom = null;
        String activite = null;
        String localisation = null;
        if (mission != null) {
            clientNom = mission.getClient() == null ? null : mission.getClient().getRaisonSociale();
            activite = mission.getTypeMission().getLibelle() + " (" + mission.getNumero() + ")";
            localisation = trajet(mission.getLieuDepart(), mission.getDestination());
        } else if (location != null) {
            clientNom = location.getClient().getRaisonSociale();
            activite = location.getTypeLocation().getLibelle() + " (" + location.getNumero() + ")";
            localisation = location.getDestination();
        }

        String marqueModele = ((camion.getMarque() == null ? "" : camion.getMarque()) + " "
                + (camion.getModele() == null ? "" : camion.getModele())).trim();

        MarqueCamion marque = camion.getMarqueReference();
        return new VueParcDto(
                camion.getId(), camion.getImmatriculation(),
                marqueModele.isEmpty() ? null : marqueModele,
                camion.libelleMarque(),
                marque == null ? null : marque.getLogoUrl(),
                marque == null ? null : marque.getCouleur(),
                marque == null ? null : marque.monogramme(),
                camion.libelleType(),
                camion.getTypeReference() == null ? null : camion.getTypeReference().getDessin(),
                camion.getStatut(), camion.getStatut().getLibelle(),
                chauffeurId, chauffeurNom, clientNom, activite, localisation,
                camion.getKilometrageActuel(), camion.getPhotoUrl());
    }

    private String trajet(String depart, String destination) {
        if (depart == null && destination == null) {
            return null;
        }
        if (depart == null) {
            return destination;
        }
        return destination == null ? depart : depart + " -> " + destination;
    }
}
