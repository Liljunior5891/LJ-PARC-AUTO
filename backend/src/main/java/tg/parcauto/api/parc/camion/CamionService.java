package tg.parcauto.api.parc.camion;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class CamionService {

    private final tg.parcauto.api.plateforme.abonnement.AbonnementService abonnement;

    private final CamionRepository repository;
    private final tg.parcauto.api.parc.affectation.AffectationRepository affectations;
    private final tg.parcauto.api.exploitation.mission.MissionRepository missions;
    private final MarqueCamionRepository marqueRepository;
    private final TypeCamionRepository typeRepository;
    private final CouleurCamionRepository couleurRepository;

    @Transactional(readOnly = true)
    public Page<CamionDto.Reponse> rechercher(String q, StatutCamion statut, Boolean actif, Pageable pageable) {
        return repository.findAll(CamionSpecifications.rechercher(q, statut, actif), pageable)
                .map(CamionDto.Reponse::de);
    }

    @Transactional(readOnly = true)
    public CamionDto.Reponse parId(Long id) {
        return CamionDto.Reponse.de(charger(id));
    }

    @Transactional(readOnly = true)
    public Camion charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Vehicule", id));
    }

    /** Referentiel des marques, pour les listes deroulantes et les logos. */
    @Transactional(readOnly = true)
    public List<MarqueCamionDto.Reponse> marques() {
        return marqueRepository.findByActifTrueOrderByOrdreAscNomAsc().stream()
                .map(MarqueCamionDto.Reponse::de)
                .toList();
    }

    /** Types de carrosserie proposes a la saisie. */
    @Transactional(readOnly = true)
    public List<ReferentielDto> types() {
        return typeRepository.findByActifTrueOrderByOrdreAscNomAsc().stream()
                .map(ReferentielDto::de)
                .toList();
    }

    /** Couleurs proposees a la saisie. */
    @Transactional(readOnly = true)
    public List<ReferentielDto> couleurs() {
        return couleurRepository.findByActifTrueOrderByOrdreAscNomAsc().stream()
                .map(ReferentielDto::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<StatutCamion, Long> repartitionParStatut() {
        Map<StatutCamion, Long> repartition = new EnumMap<>(StatutCamion.class);
        for (StatutCamion statut : StatutCamion.values()) {
            repartition.put(statut, 0L);
        }
        repository.compterParStatut().forEach(l -> repartition.put(l.getStatut(), l.getTotal()));
        return repartition;
    }

    public CamionDto.Reponse creer(CamionDto.Requete requete) {
        abonnement.verifierLimite(tg.parcauto.api.plateforme.abonnement.Ressource.VEHICULES);
        if (repository.existsByImmatriculationIgnoreCase(requete.immatriculation())) {
            throw new BusinessException("Un vehicule existe deja avec l'immatriculation " + requete.immatriculation());
        }
        Camion camion = new Camion();
        appliquer(camion, requete);
        if (requete.kilometrageInitial() != null && requete.kilometrageActuel() == null) {
            camion.setKilometrageActuel(requete.kilometrageInitial());
        }
        return CamionDto.Reponse.de(repository.save(camion));
    }

    public CamionDto.Reponse modifier(Long id, CamionDto.Requete requete) {
        Camion camion = charger(id);
        repository.findByImmatriculationIgnoreCase(requete.immatriculation())
                .filter(autre -> !autre.getId().equals(id))
                .ifPresent(autre -> {
                    throw new BusinessException("Immatriculation deja utilisee : " + requete.immatriculation());
                });
        appliquer(camion, requete);
        return CamionDto.Reponse.de(repository.save(camion));
    }

    /**
     * Location, mission et livraison posent et levent elles-memes leur statut :
     * a la main, on ne met pas un vehicule « en location » ni on ne libere un vehicule engage.
     */
    private static void verifierStatutManuel(Camion camion, StatutCamion cible) {
        if (cible.engage()) {
            throw new BusinessException("Le statut « " + cible.getLibelle() + " » vient du contrat, de la mission ou"
                    + " de la livraison : il ne se choisit pas a la main");
        }
        if (camion.getStatut().engage()) {
            throw new BusinessException("Le vehicule " + camion.getImmatriculation() + " est " + camion.getStatut().getLibelle()
                    .toLowerCase() + " : cloturez d'abord le contrat, la mission ou la livraison");
        }
    }

    /** Un chauffeur ne releve que le compteur du vehicule qui lui est confie, par sauts plausibles. */
    private static final long SAUT_MAX_CHAUFFEUR_KM = 5_000;

    public CamionDto.Reponse majKilometrage(Long id, long kilometrage) {
        Camion camion = charger(id);
        if (tg.parcauto.api.security.Terrain.chauffeurSeul()) {
            Long moi = tg.parcauto.api.security.Terrain.chauffeurId();
            boolean confie = moi != null && (affectations.affectationCouranteDuCamion(camion.getId())
                    .map(a -> a.getChauffeur().getId().equals(moi)).orElse(false)
                    || missions.missionEnCoursDuCamion(camion.getId())
                    .map(m -> m.getChauffeur() != null && m.getChauffeur().getId().equals(moi)).orElse(false));
            if (!confie) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Le vehicule " + camion.getImmatriculation() + " n'est pas le votre");
            }
            if (kilometrage - camion.getKilometrageActuel() > SAUT_MAX_CHAUFFEUR_KM) {
                throw new BusinessException("Plus de " + SAUT_MAX_CHAUFFEUR_KM + " km d'un coup : verifiez le compteur,"
                        + " ou faites corriger par le gestionnaire");
            }
        }
        if (kilometrage < camion.getKilometrageActuel()) {
            throw new BusinessException("Le nouveau kilometrage (" + kilometrage
                    + ") ne peut pas etre inferieur au kilometrage actuel (" + camion.getKilometrageActuel() + ")");
        }
        camion.setKilometrageActuel(kilometrage);
        return CamionDto.Reponse.de(repository.save(camion));
    }

    public CamionDto.Reponse majStatut(Long id, StatutCamion statut) {
        Camion camion = charger(id);
        if (statut != camion.getStatut()) {
            verifierStatutManuel(camion, statut);
        }
        camion.setStatut(statut);
        if (statut == StatutCamion.VENDU || statut == StatutCamion.HORS_SERVICE) {
            camion.setActif(false);
        }
        return CamionDto.Reponse.de(repository.save(camion));
    }

    /** Archivage logique : on ne supprime jamais un camion porteur d'historique. */
    public void archiver(Long id) {
        Camion camion = charger(id);
        camion.setActif(false);
        repository.save(camion);
    }

    private void appliquer(Camion camion, CamionDto.Requete r) {
        camion.setImmatriculation(r.immatriculation().trim().toUpperCase());
        // La marque du referentiel prime ; le libelle libre sert de repli.
        if (r.marqueId() != null) {
            MarqueCamion marque = marqueRepository.findById(r.marqueId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Marque", r.marqueId()));
            camion.setMarqueReference(marque);
            camion.setMarque(marque.getNom());
        } else {
            camion.setMarqueReference(null);
            camion.setMarque(r.marque());
        }
        camion.setModele(r.modele());
        camion.setAnnee(r.annee());
        // Le referentiel prime ; le libelle libre sert de repli.
        if (r.typeId() != null) {
            TypeCamion type = typeRepository.findById(r.typeId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Type de vehicule", r.typeId()));
            camion.setTypeReference(type);
            camion.setType(type.getNom());
        } else {
            camion.setTypeReference(null);
            camion.setType(r.type());
        }
        camion.setCategorie(r.categorie());
        camion.setNumeroChassis(r.numeroChassis());
        camion.setNumeroMoteur(r.numeroMoteur());
        if (r.couleurId() != null) {
            CouleurCamion couleur = couleurRepository.findById(r.couleurId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Couleur", r.couleurId()));
            camion.setCouleurReference(couleur);
            camion.setCouleur(couleur.getNom());
        } else {
            camion.setCouleurReference(null);
            camion.setCouleur(r.couleur());
        }
        camion.setCapaciteTonnes(r.capaciteTonnes());
        camion.setDateAcquisition(r.dateAcquisition());
        camion.setPrixAchat(r.prixAchat());
        camion.setPhotoUrl(r.photoUrl());
        camion.setGpsDeviceId(r.gpsDeviceId());
        camion.setVitesseMaxKmh(r.vitesseMaxKmh());
        camion.setObservations(r.observations());
        if (r.typeCarburant() != null) {
            camion.setTypeCarburant(r.typeCarburant());
        }
        if (r.kilometrageInitial() != null) {
            camion.setKilometrageInitial(r.kilometrageInitial());
        }
        // Le compteur ne recule pas : une fiche ouverte avant une mission ne l'efface pas en s'enregistrant.
        if (r.kilometrageActuel() != null && (camion.getId() == null || r.kilometrageActuel() > camion.getKilometrageActuel())) {
            camion.setKilometrageActuel(r.kilometrageActuel());
        }
        if (r.statut() != null && r.statut() != camion.getStatut()) {
            if (camion.getId() == null) {
                if (r.statut().engage()) {
                    throw new BusinessException("Un nouveau vehicule ne peut pas etre deja engage");
                }
                camion.setStatut(r.statut());
            } else {
                verifierStatutManuel(camion, r.statut());
                camion.setStatut(r.statut());
            }
        }
        if (r.actif() != null) {
            if (r.actif() && !camion.isActif() && camion.getId() != null) {
                abonnement.verifierLimite(tg.parcauto.api.plateforme.abonnement.Ressource.VEHICULES);
            }
            camion.setActif(r.actif());
        }
    }
}
