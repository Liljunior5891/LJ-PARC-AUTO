package tg.parcauto.api.parc.camion;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;

import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class CamionService {

    private final CamionRepository repository;

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
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Camion", id));
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
        if (repository.existsByImmatriculationIgnoreCase(requete.immatriculation())) {
            throw new BusinessException("Un camion existe deja avec l'immatriculation " + requete.immatriculation());
        }
        if (repository.existsByNumeroInterneIgnoreCase(requete.numeroInterne())) {
            throw new BusinessException("Un camion existe deja avec le numero interne " + requete.numeroInterne());
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

    public CamionDto.Reponse majKilometrage(Long id, long kilometrage) {
        Camion camion = charger(id);
        if (kilometrage < camion.getKilometrageActuel()) {
            throw new BusinessException("Le nouveau kilometrage (" + kilometrage
                    + ") ne peut pas etre inferieur au kilometrage actuel (" + camion.getKilometrageActuel() + ")");
        }
        camion.setKilometrageActuel(kilometrage);
        return CamionDto.Reponse.de(repository.save(camion));
    }

    public CamionDto.Reponse majStatut(Long id, StatutCamion statut) {
        Camion camion = charger(id);
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
        camion.setNumeroInterne(r.numeroInterne().trim());
        camion.setImmatriculation(r.immatriculation().trim().toUpperCase());
        camion.setMarque(r.marque());
        camion.setModele(r.modele());
        camion.setAnnee(r.annee());
        camion.setType(r.type());
        camion.setCategorie(r.categorie());
        camion.setNumeroChassis(r.numeroChassis());
        camion.setNumeroMoteur(r.numeroMoteur());
        camion.setCouleur(r.couleur());
        camion.setCapaciteTonnes(r.capaciteTonnes());
        camion.setDateAcquisition(r.dateAcquisition());
        camion.setPrixAchat(r.prixAchat());
        camion.setPhotoUrl(r.photoUrl());
        camion.setGpsDeviceId(r.gpsDeviceId());
        camion.setObservations(r.observations());
        if (r.typeCarburant() != null) {
            camion.setTypeCarburant(r.typeCarburant());
        }
        if (r.kilometrageInitial() != null) {
            camion.setKilometrageInitial(r.kilometrageInitial());
        }
        if (r.kilometrageActuel() != null) {
            camion.setKilometrageActuel(r.kilometrageActuel());
        }
        if (r.statut() != null) {
            camion.setStatut(r.statut());
        }
        if (r.actif() != null) {
            camion.setActif(r.actif());
        }
    }
}
