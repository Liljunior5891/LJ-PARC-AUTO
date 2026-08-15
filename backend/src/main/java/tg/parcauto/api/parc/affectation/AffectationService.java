package tg.parcauto.api.parc.affectation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.parc.camion.Camion;
import tg.parcauto.api.parc.camion.CamionService;
import tg.parcauto.api.parc.chauffeur.Chauffeur;
import tg.parcauto.api.parc.chauffeur.ChauffeurService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class AffectationService {

    private final AffectationRepository repository;
    private final CamionService camionService;
    private final ChauffeurService chauffeurService;

    @Transactional(readOnly = true)
    public Page<AffectationDto.Reponse> rechercher(Long camionId, Long chauffeurId, Boolean enCours,
                                                   Pageable pageable) {
        return repository.rechercher(camionId, chauffeurId, enCours, pageable).map(AffectationDto.Reponse::de);
    }

    @Transactional(readOnly = true)
    public List<AffectationDto.Reponse> enCours() {
        return repository.affectationsEnCours().stream().map(AffectationDto.Reponse::de).toList();
    }

    @Transactional(readOnly = true)
    public Optional<AffectationDto.Reponse> couranteDuCamion(Long camionId) {
        return repository.affectationCouranteDuCamion(camionId).map(AffectationDto.Reponse::de);
    }

    @Transactional(readOnly = true)
    public Optional<AffectationDto.Reponse> couranteDuChauffeur(Long chauffeurId) {
        return repository.affectationCouranteDuChauffeur(chauffeurId).map(AffectationDto.Reponse::de);
    }

    /**
     * Affecte un chauffeur a un camion. Un camion et un chauffeur ne peuvent avoir
     * qu'une seule affectation ouverte sur une meme periode.
     */
    public AffectationDto.Reponse affecter(AffectationDto.Requete requete) {
        if (requete.dateFin() != null && requete.dateFin().isBefore(requete.dateDebut())) {
            throw new BusinessException("La date de fin precede la date de debut");
        }
        Camion camion = camionService.charger(requete.camionId());
        Chauffeur chauffeur = chauffeurService.charger(requete.chauffeurId());

        if (!camion.getStatut().exploitable()) {
            throw new BusinessException("Le camion " + camion.getImmatriculation()
                    + " n'est pas exploitable (statut " + camion.getStatut().getLibelle() + ")");
        }
        if (!chauffeur.isActif()) {
            throw new BusinessException("Le chauffeur " + chauffeur.nomComplet() + " est archive");
        }

        List<Affectation> conflits = repository.chevauchements(
                camion.getId(), chauffeur.getId(), requete.dateDebut(), requete.dateFin());
        for (Affectation conflit : conflits) {
            if (!conflit.chevauche(requete.dateDebut(), requete.dateFin())) {
                continue;
            }
            if (conflit.getCamion().getId().equals(camion.getId())) {
                throw new BusinessException("Le camion " + camion.getImmatriculation()
                        + " est deja affecte a " + conflit.getChauffeur().nomComplet()
                        + " depuis le " + conflit.getDateDebut());
            }
            throw new BusinessException("Le chauffeur " + chauffeur.nomComplet()
                    + " est deja affecte au camion " + conflit.getCamion().getImmatriculation()
                    + " depuis le " + conflit.getDateDebut());
        }

        Affectation affectation = new Affectation();
        affectation.setCamion(camion);
        affectation.setChauffeur(chauffeur);
        affectation.setDateDebut(requete.dateDebut());
        affectation.setDateFin(requete.dateFin());
        affectation.setMotif(requete.motif());
        affectation.setObservations(requete.observations());
        return AffectationDto.Reponse.de(repository.save(affectation));
    }

    /** Cloture une affectation en cours : l'historique est conserve. */
    public AffectationDto.Reponse cloturer(Long id, AffectationDto.Cloture cloture) {
        Affectation affectation = charger(id);
        if (!affectation.enCours()) {
            throw new BusinessException("Cette affectation est deja cloturee le " + affectation.getDateFin());
        }
        if (cloture.dateFin().isBefore(affectation.getDateDebut())) {
            throw new BusinessException("La date de fin precede la date de debut de l'affectation");
        }
        affectation.setDateFin(cloture.dateFin());
        if (cloture.observations() != null && !cloture.observations().isBlank()) {
            affectation.setObservations(cloture.observations());
        }
        return AffectationDto.Reponse.de(repository.save(affectation));
    }

    public void supprimer(Long id) {
        Affectation affectation = charger(id);
        if (affectation.getDateDebut().isBefore(LocalDate.now())) {
            throw new BusinessException("Une affectation deja commencee ne peut etre supprimee : cloturez-la");
        }
        repository.delete(affectation);
    }

    private Affectation charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Affectation", id));
    }
}
