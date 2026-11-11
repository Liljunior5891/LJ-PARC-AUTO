package tg.parcauto.api.maintenance.entretien;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.finance.depense.DepenseDto;
import tg.parcauto.api.finance.depense.DepenseService;
import tg.parcauto.api.finance.depense.SousCategorieDepense;
import tg.parcauto.api.parc.camion.Camion;
import tg.parcauto.api.parc.camion.CamionRepository;
import tg.parcauto.api.parc.camion.CamionService;
import tg.parcauto.api.parc.camion.StatutCamion;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Suivi des entretiens, chapitre 19 du cahier des charges.
 * <p>
 * La realisation d'une intervention alimente automatiquement les depenses du
 * camion : sans ce lien, le cout de maintenance echapperait au calcul de
 * rentabilite du chapitre 2.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EntretienService {

    private final EntretienRepository repository;
    private final tg.parcauto.api.maintenance.EtatVehicule etatVehicule;
    private final CamionService camionService;
    private final CamionRepository camionRepository;
    private final DepenseService depenseService;
    private final NumerotationService numerotation;

    @Transactional(readOnly = true)
    public List<EntretienDto.Reponse> ouverts() {
        return repository.ouverts().stream().map(EntretienDto.Reponse::de).toList();
    }

    @Transactional(readOnly = true)
    public List<EntretienDto.Reponse> historiqueDuCamion(Long camionId) {
        return repository.historiqueDuCamion(camionId).stream().map(EntretienDto.Reponse::de).toList();
    }

    /** Interventions dont l'echeance est atteinte ou proche (chapitre 19). */
    @Transactional(readOnly = true)
    public List<EntretienDto.Reponse> alertes() {
        return repository.ouverts().stream()
                .filter(Entretien::echeanceProche)
                .sorted(Comparator.comparing(Entretien::enRetard).reversed())
                .map(EntretienDto.Reponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public EntretienDto.Reponse parId(Long id) {
        return EntretienDto.Reponse.de(charger(id));
    }

    public EntretienDto.Reponse planifier(EntretienDto.Requete requete) {
        Entretien entretien = new Entretien();
        entretien.setNumero(numerotation.prochainNumero("ENT"));
        appliquer(entretien, requete);
        if (entretien.getDatePrevue() == null && entretien.getKilometragePrevu() == null) {
            throw new BusinessException(
                    "Renseignez une echeance : une date prevue, un kilometrage prevu, ou les deux");
        }
        return EntretienDto.Reponse.de(repository.save(entretien));
    }

    public EntretienDto.Reponse modifier(Long id, EntretienDto.Requete requete) {
        Entretien entretien = charger(id);
        if (entretien.getStatut().clos()) {
            throw new BusinessException("Cette intervention est " + entretien.getStatut().getLibelle()
                    + " : elle n'est plus modifiable");
        }
        // A l'atelier, le vehicule est immobilise : on ne change pas de vehicule en cours d'intervention.
        if (entretien.getStatut() == StatutEntretien.EN_COURS && !entretien.getCamion().getId().equals(requete.camionId())) {
            throw new BusinessException("L'intervention " + entretien.getNumero() + " est en cours sur "
                    + entretien.getCamion().getImmatriculation() + " : annulez-la et planifiez-en une autre pour l'autre vehicule");
        }
        appliquer(entretien, requete);
        return EntretienDto.Reponse.de(repository.save(entretien));
    }

    /**
     * Refait la depense d'une intervention realisee dont la depense a ete
     * refusee ou annulee (ou jamais creee) : le cout revient dans les resultats.
     */
    public EntretienDto.Reponse refaireDepense(Long id) {
        Entretien entretien = charger(id);
        if (entretien.getStatut() != StatutEntretien.REALISE || entretien.getCout() == null || entretien.getCout().signum() <= 0) {
            throw new BusinessException("Seule une intervention realisee avec un cout a une depense");
        }
        var actuelle = entretien.getDepense();
        if (actuelle != null && actuelle.getStatut() != tg.parcauto.api.finance.depense.StatutDepense.REFUSEE
                && actuelle.getStatut() != tg.parcauto.api.finance.depense.StatutDepense.ANNULEE) {
            throw new BusinessException("La depense " + actuelle.getNumero() + " de cette intervention existe deja");
        }
        enregistrerDepense(entretien, new EntretienDto.Realisation(
                entretien.getDateRealisation(), entretien.getKilometrageRealise(), entretien.getCout(),
                entretien.getFournisseur(), entretien.getPiecesRemplacees(), entretien.getImmobilisationJours(),
                null, true, false));
        return EntretienDto.Reponse.de(repository.save(entretien));
    }

    /** Passe l'intervention a l'atelier : le camion est immobilise. */
    public EntretienDto.Reponse demarrer(Long id) {
        Entretien entretien = charger(id);
        if (entretien.getStatut() != StatutEntretien.PLANIFIE) {
            throw new BusinessException("Seule une intervention planifiee peut etre demarree");
        }
        Camion camion = entretien.getCamion();
        if (camion.getStatut().engage()) {
            throw new BusinessException("Le vehicule " + camion.getImmatriculation() + " est "
                    + camion.getStatut().getLibelle().toLowerCase() + " : il passera a l'atelier a son retour");
        }
        entretien.setStatut(StatutEntretien.EN_COURS);
        camion.setStatut(StatutCamion.EN_MAINTENANCE);
        camionRepository.save(camion);
        return EntretienDto.Reponse.de(repository.save(entretien));
    }

    /**
     * Cloture l'intervention : libere le camion, enregistre la depense et
     * planifie l'occurrence suivante si l'entretien est periodique.
     */
    public EntretienDto.Reponse realiser(Long id, EntretienDto.Realisation realisation) {
        Entretien entretien = charger(id);
        if (entretien.getStatut().clos()) {
            throw new BusinessException("Cette intervention est deja close");
        }
        Camion camion = entretien.getCamion();
        if (realisation.kilometrageRealise() < camion.getKilometrageInitial()) {
            throw new BusinessException("Le kilometrage releve est anterieur a la mise en service du vehicule");
        }

        entretien.setStatut(StatutEntretien.REALISE);
        entretien.setDateRealisation(realisation.dateRealisation());
        entretien.setKilometrageRealise(realisation.kilometrageRealise());
        entretien.setCout(realisation.cout());
        entretien.setPiecesRemplacees(realisation.piecesRemplacees());
        entretien.setImmobilisationJours(realisation.immobilisationJours());
        if (realisation.fournisseur() != null && !realisation.fournisseur().isBlank()) {
            entretien.setFournisseur(realisation.fournisseur());
        }
        if (realisation.observations() != null && !realisation.observations().isBlank()) {
            entretien.setObservations(realisation.observations());
        }

        // Le compteur du camion suit le releve de l'atelier.
        tg.parcauto.api.parc.camion.CompteurKilometrique.avancer(camion, realisation.kilometrageRealise(), "Entretien realise");
        if (camion.getStatut() == StatutCamion.EN_MAINTENANCE) {
            // Sorti d'atelier : accidente si l'incident reste ouvert, en location si le contrat court, etc.
            repository.saveAndFlush(entretien);
            etatVehicule.recalculer(camion);
        }
        camionRepository.save(camion);

        if (!Boolean.FALSE.equals(realisation.genererDepense())
                && realisation.cout() != null && realisation.cout().signum() > 0) {
            enregistrerDepense(entretien, realisation);
        }
        Entretien enregistre = repository.save(entretien);

        if (!Boolean.FALSE.equals(realisation.planifierSuivant())) {
            planifierSuivant(enregistre);
        }
        return EntretienDto.Reponse.de(enregistre);
    }

    public EntretienDto.Reponse annuler(Long id, String motif) {
        Entretien entretien = charger(id);
        if (entretien.getStatut().clos()) {
            throw new BusinessException("Cette intervention est deja close");
        }
        boolean immobilisait = entretien.getStatut().immobilise();
        entretien.setStatut(StatutEntretien.ANNULE);
        if (motif != null && !motif.isBlank()) {
            entretien.setObservations(motif);
        }
        if (immobilisait) {
            Camion camion = entretien.getCamion();
            if (camion.getStatut() == StatutCamion.EN_MAINTENANCE) {
                repository.saveAndFlush(entretien);
                etatVehicule.recalculer(camion);
                camionRepository.save(camion);
            }
        }
        return EntretienDto.Reponse.de(repository.save(entretien));
    }

    @Transactional(readOnly = true)
    public Entretien charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Entretien", id));
    }

    /** Cree l'occurrence suivante d'un entretien periodique. */
    private void planifierSuivant(Entretien realise) {
        Long prochainKm = realise.prochainKilometrage();
        LocalDate prochaineDate = realise.prochaineDate();
        if (prochainKm == null && prochaineDate == null) {
            return;
        }
        Entretien suivant = new Entretien();
        suivant.setNumero(numerotation.prochainNumero("ENT"));
        suivant.setCamion(realise.getCamion());
        suivant.setType(realise.getType());
        suivant.setLibelle(realise.getLibelle());
        suivant.setStatut(StatutEntretien.PLANIFIE);
        suivant.setKilometragePrevu(prochainKm);
        suivant.setDatePrevue(prochaineDate);
        suivant.setPeriodiciteKm(realise.getPeriodiciteKm());
        suivant.setPeriodiciteJours(realise.getPeriodiciteJours());
        suivant.setFournisseur(realise.getFournisseur());
        repository.save(suivant);
    }

    private void enregistrerDepense(Entretien entretien, EntretienDto.Realisation realisation) {
        var requete = new DepenseDto.Requete(
                entretien.getCamion().getId(), null, null, null,
                natureDe(entretien.getType()),
                entretien.getType().getLibelle() + " — " + entretien.getLibelle(),
                realisation.cout(), realisation.dateRealisation(),
                entretien.getFournisseur(), null, entretien.getNumero(), null,
                realisation.kilometrageRealise(), null, null, null, false,
                "Genere par l'intervention " + entretien.getNumero());
        var depense = depenseService.creer(requete);
        entretien.setDepense(depenseService.charger(depense.id()));
    }

    /** Rattache l'intervention a la nature de depense correspondante. */
    private SousCategorieDepense natureDe(TypeEntretien type) {
        return switch (type) {
            case VIDANGE -> SousCategorieDepense.VIDANGE;
            case FILTRES -> SousCategorieDepense.FILTRES;
            case FREINS -> SousCategorieDepense.FREINS;
            case COURROIE, REVISION_GENERALE, REPARATION, CARROSSERIE -> SousCategorieDepense.REPARATION;
            case VISITE_TECHNIQUE -> SousCategorieDepense.VISITE_TECHNIQUE;
            case AUTRE -> SousCategorieDepense.AUTRE;
        };
    }

    private void appliquer(Entretien e, EntretienDto.Requete r) {
        e.setCamion(camionService.charger(r.camionId()));
        e.setType(r.type());
        e.setLibelle(r.libelle().trim());
        e.setDatePrevue(r.datePrevue());
        e.setKilometragePrevu(r.kilometragePrevu());
        e.setFournisseur(r.fournisseur());
        e.setIntervenant(r.intervenant());
        e.setObservations(r.observations());
        // A defaut de periodicite saisie, celle conseillee pour ce type s'applique.
        e.setPeriodiciteKm(r.periodiciteKm() != null
                ? r.periodiciteKm() : r.type().getPeriodiciteKmParDefaut());
        e.setPeriodiciteJours(r.periodiciteJours() != null
                ? r.periodiciteJours() : r.type().getPeriodiciteJoursParDefaut());
    }
}
