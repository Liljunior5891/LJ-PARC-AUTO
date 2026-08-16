package tg.parcauto.api.maintenance.pneu;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.parc.camion.Camion;
import tg.parcauto.api.parc.camion.CamionService;

import java.util.List;

/**
 * Suivi individuel des pneumatiques, chapitre 20 du cahier des charges.
 * <p>
 * Chaque pneu est trace du stock au rebut : son kilometrage propre permet de
 * comparer la tenue des marques par leur cout au kilometre.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PneuService {

    private final PneuRepository repository;
    private final CamionService camionService;

    @Transactional(readOnly = true)
    public List<PneuDto.Reponse> lister() {
        return repository.findAll().stream().map(PneuDto.Reponse::de).toList();
    }

    @Transactional(readOnly = true)
    public List<PneuDto.Reponse> duCamion(Long camionId) {
        return repository.duCamion(camionId).stream().map(PneuDto.Reponse::de).toList();
    }

    /** Pneus montes arrivant en fin de vie ou declares hors usage. */
    @Transactional(readOnly = true)
    public List<PneuDto.Reponse> aRemplacer() {
        return repository.montes().stream()
                .filter(Pneu::aRemplacer)
                .map(PneuDto.Reponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public PneuDto.Reponse parId(Long id) {
        return PneuDto.Reponse.de(charger(id));
    }

    public PneuDto.Reponse creer(PneuDto.Requete requete) {
        if (repository.existsByReferenceIgnoreCase(requete.reference())) {
            throw new BusinessException("Un pneu porte deja la reference " + requete.reference());
        }
        Pneu pneu = new Pneu();
        pneu.setReference(requete.reference().trim().toUpperCase());
        appliquer(pneu, requete);
        return PneuDto.Reponse.de(repository.save(pneu));
    }

    public PneuDto.Reponse modifier(Long id, PneuDto.Requete requete) {
        Pneu pneu = charger(id);
        appliquer(pneu, requete);
        return PneuDto.Reponse.de(repository.save(pneu));
    }

    /** Monte le pneu a une position : la position doit etre libre. */
    public PneuDto.Reponse monter(Long id, PneuDto.Montage montage) {
        Pneu pneu = charger(id);
        if (pneu.getStatut() == StatutPneu.MONTE) {
            throw new BusinessException("Ce pneu est deja monte sur "
                    + pneu.getCamion().getImmatriculation() + " en position " + pneu.getPosition());
        }
        if (pneu.getStatut() == StatutPneu.REBUTE) {
            throw new BusinessException("Un pneu rebute ne peut pas etre remonte");
        }

        Camion camion = camionService.charger(montage.camionId());
        String position = montage.position().trim().toUpperCase();
        repository.monteEnPosition(camion.getId(), position).ifPresent(occupant -> {
            throw new BusinessException("La position " + position + " de "
                    + camion.getImmatriculation() + " porte deja le pneu " + occupant.getReference()
                    + " : deposez-le d'abord");
        });

        long kilometrage = montage.kilometrageMontage() == null
                ? camion.getKilometrageActuel() : montage.kilometrageMontage();

        pneu.setCamion(camion);
        pneu.setPosition(position);
        pneu.setStatut(StatutPneu.MONTE);
        pneu.setDateMontage(montage.dateMontage());
        pneu.setKilometrageMontage(kilometrage);
        pneu.setDateDepose(null);
        pneu.setKilometrageDepose(null);
        pneu.setMotifDepose(null);
        return PneuDto.Reponse.de(repository.save(pneu));
    }

    /** Depose le pneu : retour en stock ou mise au rebut. */
    public PneuDto.Reponse deposer(Long id, PneuDto.Depose depose) {
        Pneu pneu = charger(id);
        if (pneu.getStatut() != StatutPneu.MONTE) {
            throw new BusinessException("Seul un pneu monte peut etre depose");
        }
        long kilometrage = depose.kilometrageDepose() == null
                ? pneu.getCamion().getKilometrageActuel() : depose.kilometrageDepose();
        if (pneu.getKilometrageMontage() != null && kilometrage < pneu.getKilometrageMontage()) {
            throw new BusinessException("Le kilometrage de depose (" + kilometrage
                    + ") precede celui du montage (" + pneu.getKilometrageMontage() + ")");
        }

        pneu.setDateDepose(depose.dateDepose());
        pneu.setKilometrageDepose(kilometrage);
        pneu.setEtat(depose.etat());
        pneu.setMotifDepose(depose.motif());
        pneu.setPosition(null);
        boolean rebut = Boolean.TRUE.equals(depose.rebuter()) || depose.etat() == EtatPneu.HORS_USAGE;
        pneu.setStatut(rebut ? StatutPneu.REBUTE : StatutPneu.DEPOSE);
        return PneuDto.Reponse.de(repository.save(pneu));
    }

    @Transactional(readOnly = true)
    public Pneu charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Pneu", id));
    }

    private void appliquer(Pneu p, PneuDto.Requete r) {
        p.setMarque(r.marque());
        p.setDimension(r.dimension());
        p.setNumeroSerie(r.numeroSerie());
        p.setDateAchat(r.dateAchat());
        p.setPrixAchat(r.prixAchat());
        p.setDureeViePrevueKm(r.dureeViePrevueKm());
        p.setObservations(r.observations());
        if (r.etat() != null) {
            p.setEtat(r.etat());
        }
    }
}
