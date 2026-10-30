package tg.parcauto.api.finance.recette;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.exploitation.client.ClientService;
import tg.parcauto.api.exploitation.location.Location;
import tg.parcauto.api.exploitation.location.LocationDto;
import tg.parcauto.api.exploitation.location.LocationService;
import tg.parcauto.api.exploitation.mission.MissionService;
import tg.parcauto.api.finance.facture.FactureService;
import tg.parcauto.api.parc.camion.CamionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class RecetteService {

    private final RecetteRepository repository;
    private final CamionService camionService;
    private final ClientService clientService;
    private final LocationService locationService;
    private final MissionService missionService;
    private final FactureService factureService;
    private final NumerotationService numerotation;

    @Transactional(readOnly = true)
    public Page<RecetteDto.Reponse> rechercher(String q, TypeRecette type, Long camionId, Long clientId,
                                               LocalDate debut, LocalDate fin, Pageable pageable) {
        return repository
                .findAll(RecetteSpecifications.rechercher(q, type, camionId, clientId, debut, fin), pageable)
                .map(RecetteDto.Reponse::de);
    }

    @Transactional(readOnly = true)
    public RecetteDto.Reponse parId(Long id) {
        return RecetteDto.Reponse.de(charger(id));
    }

    @Transactional(readOnly = true)
    public List<RecetteDto.LigneType> repartitionParType(LocalDate debut, LocalDate fin, Long camionId) {
        var lignes = camionId == null
                ? repository.totauxParType(debut, fin)
                : repository.totauxParTypePourCamion(camionId, debut, fin);
        return lignes.stream()
                .map(l -> new RecetteDto.LigneType(l.getType(), l.getType().getLibelle(),
                        l.getTotal(), l.getNombre()))
                .sorted((a, b) -> b.total().compareTo(a.total()))
                .toList();
    }

    public RecetteDto.Reponse creer(RecetteDto.Requete requete) {
        Recette recette = new Recette();
        recette.setNumero(numerotation.prochainNumero("REC"));
        appliquer(recette, requete);
        return RecetteDto.Reponse.de(facturer(repository.save(recette)));
    }

    /**
     * Emet la facture qui accompagne la recette.
     * <p>
     * Une recette est une prestation rendue : le client doit en recevoir la
     * facture. Sans client, il n'y a personne a facturer et la recette reste
     * seule — une recette d'appoint n'a pas de destinataire.
     */
    private Recette facturer(Recette recette) {
        if (recette.getClient() == null || recette.getFacture() != null) {
            return recette;
        }
        factureService.facturerRecette(recette);
        return recette;
    }

    /**
     * Enregistre une recette de location avec les conditions du contrat.
     * <p>
     * Le contrat est cree dans le meme mouvement : c'est lui qui porte la
     * periode, le modele tarifaire et le tarif, et qui calcule le montant. La
     * recette s'y rattache, de sorte que le camion se voit crediter une somme
     * dont l'origine reste verifiable.
     */
    public RecetteDto.Reponse enregistrerLocation(RecetteDto.RequeteLocation demande) {
        if (demande.dateFin() != null && demande.dateFin().isBefore(demande.dateDebut())) {
            throw new BusinessException("La date de fin precede la date de debut de la location");
        }
        // Verifie avant de creer le contrat : sinon l'echec porterait sur
        // l'engagement du camion et masquerait la vraie cause.
        if (demande.tarif().signum() <= 0) {
            throw new BusinessException("Le tarif de la location doit etre superieur a zero");
        }
        if (demande.typeLocation().baseSurKilometrage()
                && (demande.kilometrage() == null || demande.kilometrage() <= 0)) {
            throw new BusinessException(
                    "Le kilometrage est obligatoire pour une location au kilometre");
        }

        LocationDto.Requete contrat = new LocationDto.Requete(
                demande.clientId(), demande.camionId(), demande.chauffeurId(),
                demande.typeLocation(), demande.dateDebut(), demande.dateFin(), demande.tarif(),
                demande.lieuDepart(), demande.destination(), demande.kilometrage(),
                null, null, null, null, demande.observations());
        Location location = locationService.creerPourRecette(contrat, demande.kilometrage());

        BigDecimal montant = location.montant();
        if (montant.signum() == 0) {
            throw new BusinessException(
                    "Le montant calcule est nul : verifiez le tarif, la periode et le kilometrage");
        }

        Recette recette = new Recette();
        recette.setNumero(numerotation.prochainNumero("REC"));
        recette.setCamion(location.getCamion());
        recette.setClient(location.getClient());
        recette.setLocation(location);
        recette.setType(TypeRecette.LOCATION);
        recette.setLibelle(location.getTypeLocation().getLibelle() + " — contrat " + location.getNumero());
        recette.setMontant(montant);
        recette.setDateRecette(demande.dateRecette());
        recette.setModePaiement(demande.modePaiement());
        recette.setReference(demande.reference());
        recette.setObservations(demande.observations());
        return RecetteDto.Reponse.de(facturer(repository.save(recette)));
    }

    public RecetteDto.Reponse modifier(Long id, RecetteDto.Requete requete) {
        Recette recette = charger(id);
        appliquer(recette, requete);
        return RecetteDto.Reponse.de(repository.save(recette));
    }

    public void supprimer(Long id) {
        Recette recette = charger(id);
        if (recette.getFacture() != null) {
            throw new BusinessException("Cette recette est rattachee a la facture "
                    + recette.getFacture().getNumero() + " : detachez-la avant suppression");
        }
        repository.delete(recette);
    }

    @Transactional(readOnly = true)
    public Recette charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Recette", id));
    }

    private void appliquer(Recette r, RecetteDto.Requete requete) {
        if (requete.dateRecette().isAfter(LocalDate.now().plusDays(1))) {
            throw new BusinessException("La date de la recette ne peut pas etre dans le futur");
        }
        r.setCamion(camionService.charger(requete.camionId()));
        r.setClient(requete.clientId() == null ? null : clientService.charger(requete.clientId()));
        r.setLocation(requete.locationId() == null ? null : locationService.charger(requete.locationId()));
        r.setMission(requete.missionId() == null ? null : missionService.charger(requete.missionId()));
        r.setType(requete.type());
        r.setLibelle(requete.libelle().trim());
        r.setMontant(requete.montant());
        r.setDateRecette(requete.dateRecette());
        r.setModePaiement(requete.modePaiement());
        r.setReference(requete.reference());
        r.setJustificatif(requete.justificatif());
        r.setObservations(requete.observations());
        if (requete.dateDebut() != null && requete.dateFin() != null
                && requete.dateFin().isBefore(requete.dateDebut())) {
            throw new BusinessException("La date de fin ne peut pas preceder la date de debut");
        }
        r.setDateDebut(requete.dateDebut());
        r.setDateFin(requete.dateFin());
        r.setLieuDepart(texte(requete.lieuDepart()));
        r.setDestination(texte(requete.destination()));
        r.setMarchandise(texte(requete.marchandise()));
        r.setKilometres(requete.kilometres());

        // Coherence : une recette rattachee a une mission porte sur le camion de la mission.
        if (r.getMission() != null && !r.getMission().getCamion().getId().equals(r.getCamion().getId())) {
            throw new BusinessException("La mission " + r.getMission().getNumero()
                    + " porte sur un autre vehicule que celui de la recette");
        }
        if (r.getLocation() != null && !r.getLocation().getCamion().getId().equals(r.getCamion().getId())) {
            throw new BusinessException("Le contrat " + r.getLocation().getNumero()
                    + " porte sur un autre vehicule que celui de la recette");
        }
        if (r.getClient() == null) {
            if (r.getMission() != null) {
                r.setClient(r.getMission().getClient());
            } else if (r.getLocation() != null) {
                r.setClient(r.getLocation().getClient());
            }
        }
    }

    /** Texte facultatif : vide ou blanc vaut absent. */
    private static String texte(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }
}
