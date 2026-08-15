package tg.parcauto.api.finance.depense;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.exploitation.location.LocationService;
import tg.parcauto.api.exploitation.mission.MissionService;
import tg.parcauto.api.parc.camion.Camion;
import tg.parcauto.api.parc.camion.CamionRepository;
import tg.parcauto.api.parc.camion.CamionService;
import tg.parcauto.api.parc.chauffeur.ChauffeurService;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DepenseService {

    private final DepenseRepository repository;
    private final CamionService camionService;
    private final CamionRepository camionRepository;
    private final ChauffeurService chauffeurService;
    private final MissionService missionService;
    private final LocationService locationService;
    private final NumerotationService numerotation;

    @Transactional(readOnly = true)
    public Page<DepenseDto.Reponse> rechercher(String q, CategorieDepense categorie,
                                               SousCategorieDepense sousCategorie, Long camionId,
                                               Long chauffeurId, LocalDate debut, LocalDate fin,
                                               Pageable pageable) {
        return repository
                .findAll(DepenseSpecifications.rechercher(q, categorie, sousCategorie, camionId,
                        chauffeurId, debut, fin), pageable)
                .map(DepenseDto.Reponse::de);
    }

    @Transactional(readOnly = true)
    public DepenseDto.Reponse parId(Long id) {
        return DepenseDto.Reponse.de(charger(id));
    }

    /** Reponses aux questions du chapitre 17, au niveau des categories. */
    @Transactional(readOnly = true)
    public List<DepenseDto.LigneCategorie> repartitionParCategorie(LocalDate debut, LocalDate fin,
                                                                   Long camionId) {
        var lignes = camionId == null
                ? repository.totauxParCategorie(debut, fin)
                : repository.totauxParCategoriePourCamion(camionId, debut, fin);
        return lignes.stream()
                .map(l -> new DepenseDto.LigneCategorie(l.getCategorie(), l.getCategorie().getLibelle(),
                        l.getTotal(), l.getNombre()))
                .sorted((a, b) -> b.total().compareTo(a.total()))
                .toList();
    }

    /** Detail par nature pour un camion : vidange, pneus, peage, assurance... */
    @Transactional(readOnly = true)
    public List<DepenseDto.LigneNature> repartitionParNature(Long camionId, LocalDate debut, LocalDate fin) {
        return repository.totauxParNaturePourCamion(camionId, debut, fin).stream()
                .map(l -> new DepenseDto.LigneNature(l.getSousCategorie(), l.getSousCategorie().getLibelle(),
                        l.getSousCategorie().getCategorie().getLibelle(), l.getTotal(), l.getNombre()))
                .sorted((a, b) -> b.total().compareTo(a.total()))
                .toList();
    }

    public DepenseDto.Reponse creer(DepenseDto.Requete requete) {
        Depense depense = new Depense();
        depense.setNumero(numerotation.prochainNumero("DEP"));
        appliquer(depense, requete);
        Depense enregistree = repository.save(depense);
        mettreAJourCompteurCamion(enregistree);
        return DepenseDto.Reponse.de(enregistree);
    }

    public DepenseDto.Reponse modifier(Long id, DepenseDto.Requete requete) {
        Depense depense = charger(id);
        appliquer(depense, requete);
        return DepenseDto.Reponse.de(repository.save(depense));
    }

    public void supprimer(Long id) {
        repository.delete(charger(id));
    }

    @Transactional(readOnly = true)
    public Depense charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Depense", id));
    }

    /** Un releve de compteur saisi lors d'un plein fait avancer le kilometrage du camion. */
    private void mettreAJourCompteurCamion(Depense depense) {
        Long releve = depense.getKilometrage();
        Camion camion = depense.getCamion();
        if (releve != null && releve > camion.getKilometrageActuel()) {
            camion.setKilometrageActuel(releve);
            camionRepository.save(camion);
        }
    }

    private void appliquer(Depense d, DepenseDto.Requete r) {
        if (r.dateDepense().isAfter(LocalDate.now().plusDays(1))) {
            throw new BusinessException("La date de la depense ne peut pas etre dans le futur");
        }

        Camion camion = camionService.charger(r.camionId());
        d.setCamion(camion);
        d.setChauffeur(r.chauffeurId() == null ? null : chauffeurService.charger(r.chauffeurId()));
        d.setMission(r.missionId() == null ? null : missionService.charger(r.missionId()));
        d.setLocation(r.locationId() == null ? null : locationService.charger(r.locationId()));
        d.setSousCategorie(r.sousCategorie());
        d.synchroniserCategorie();
        d.setLibelle(r.libelle().trim());
        d.setMontant(r.montant());
        d.setDateDepense(r.dateDepense());
        d.setFournisseur(r.fournisseur());
        d.setModePaiement(r.modePaiement());
        d.setReference(r.reference());
        d.setJustificatif(r.justificatif());
        d.setKilometrage(r.kilometrage());
        d.setStation(r.station());
        d.setQuantiteLitres(r.quantiteLitres());
        d.setPrixLitre(r.prixLitre());
        d.setObservations(r.observations());
        d.setAChargeClient(Boolean.TRUE.equals(r.aChargeClient()));

        if (r.sousCategorie().estCarburant()) {
            if (r.quantiteLitres() == null) {
                throw new BusinessException("La quantite en litres est obligatoire pour un plein de carburant");
            }
            if (r.kilometrage() == null) {
                throw new BusinessException(
                        "Le releve du compteur est obligatoire pour un plein : il conditionne le calcul de consommation");
            }
        }
        if (d.getMission() != null && !d.getMission().getCamion().getId().equals(camion.getId())) {
            throw new BusinessException("La mission " + d.getMission().getNumero()
                    + " porte sur un autre camion que celui de la depense");
        }
    }
}
