package tg.parcauto.api.finance.facture;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.exploitation.client.Client;
import tg.parcauto.api.exploitation.client.ClientService;
import tg.parcauto.api.exploitation.location.Location;
import tg.parcauto.api.exploitation.location.LocationService;
import tg.parcauto.api.exploitation.mission.Mission;
import tg.parcauto.api.exploitation.mission.MissionService;
import tg.parcauto.api.exploitation.mission.StatutMission;
import tg.parcauto.api.finance.recette.Recette;
import tg.parcauto.api.finance.recette.RecetteRepository;
import tg.parcauto.api.finance.recette.TypeRecette;
import tg.parcauto.api.parametrage.SocieteService;
import tg.parcauto.api.parc.camion.CamionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class FactureService {

    private static final int DELAI_PAIEMENT_DEFAUT = 30;

    private final FactureRepository repository;
    private final PaiementRepository paiementRepository;
    private final RecetteRepository recetteRepository;
    private final SocieteService societeService;
    private final ClientService clientService;
    private final CamionService camionService;
    private final LocationService locationService;
    private final MissionService missionService;
    private final NumerotationService numerotation;

    @Transactional(readOnly = true)
    public Page<FactureDto.Reponse> rechercher(String q, StatutFacture statut, Long clientId,
                                               LocalDate debut, LocalDate fin, Pageable pageable) {
        return repository.findAll(FactureSpecifications.rechercher(q, statut, clientId, debut, fin), pageable)
                .map(FactureDto.Reponse::resume);
    }

    @Transactional(readOnly = true)
    public FactureDto.Reponse parId(Long id) {
        return FactureDto.Reponse.complete(charger(id));
    }

    /** Etat des creances : facture, encaisse, restant du (chapitre 28). */
    @Transactional(readOnly = true)
    public FactureDto.Creances creances() {
        List<Facture> impayees = repository.impayees();
        BigDecimal facture = repository.totalFactureImpaye();
        BigDecimal paye = repository.totalDejaRegleSurImpayees();
        long enRetard = impayees.stream().filter(Facture::enRetard).count();
        return new FactureDto.Creances(impayees.size(), facture, paye,
                facture.subtract(paye).max(BigDecimal.ZERO), enRetard);
    }

    @Transactional(readOnly = true)
    public List<FactureDto.Reponse> impayees() {
        return repository.impayees().stream().map(FactureDto.Reponse::resume).toList();
    }

    public FactureDto.Reponse creer(FactureDto.Requete requete) {
        Facture facture = new Facture();
        facture.setNumero(numerotation.prochainNumero("FAC"));
        appliquer(facture, requete);
        return FactureDto.Reponse.complete(repository.save(facture));
    }

    public FactureDto.Reponse modifier(Long id, FactureDto.Requete requete) {
        Facture facture = charger(id);
        if (!facture.getStatut().modifiable()) {
            throw new BusinessException("La facture " + facture.getNumero()
                    + " est emise : elle ne peut plus etre modifiee. Annulez-la et emettez un avoir.");
        }
        facture.getLignes().clear();
        appliquer(facture, requete);
        return FactureDto.Reponse.complete(repository.save(facture));
    }

    /**
     * Genere une facture a partir d'un contrat de location : le montant est
     * calcule par le modele tarifaire du contrat (chapitre 12).
     */
    public FactureDto.Reponse genererDepuisContrat(FactureDto.DepuisContrat requete) {
        Location contrat = locationService.charger(requete.locationId());
        Client client = contrat.getClient();

        Facture facture = nouvelleFacture(client, requete.dateEmission(),
                requete.delaiPaiementJours());
        facture.setLocation(contrat);
        facture.setPeriodeDebut(contrat.getDateDebut());
        facture.setPeriodeFin(contrat.finRetenue());

        FactureLigne ligne = new FactureLigne();
        ligne.setCamion(contrat.getCamion());
        ligne.setDesignation(contrat.getTypeLocation().getLibelle() + " — contrat " + contrat.getNumero()
                + " (" + contrat.getCamion().getImmatriculation() + ")");
        ligne.setQuantite(BigDecimal.ONE);
        ligne.setPrixUnitaire(contrat.montant());
        ligne.setUnite("forfait");
        facture.ajouterLigne(ligne);
        facture.recalculer();

        return FactureDto.Reponse.complete(repository.save(facture));
    }

    /** Genere une facture a partir d'une mission terminee. */
    public FactureDto.Reponse genererDepuisMission(FactureDto.DepuisMission requete) {
        Mission mission = missionService.charger(requete.missionId());
        if (mission.getClient() == null) {
            throw new BusinessException("La mission " + mission.getNumero()
                    + " n'a pas de client : impossible de la facturer");
        }
        if (mission.getStatut() != StatutMission.TERMINEE) {
            throw new BusinessException("Seule une mission terminee peut etre facturee (statut actuel : "
                    + mission.getStatut().getLibelle() + ")");
        }
        BigDecimal montant = mission.recetteRetenue();
        if (montant == null || montant.signum() == 0) {
            throw new BusinessException("Aucune recette n'est renseignee sur la mission " + mission.getNumero());
        }

        Facture facture = nouvelleFacture(mission.getClient(), requete.dateEmission(),
                requete.delaiPaiementJours());
        facture.setMission(mission);

        FactureLigne ligne = new FactureLigne();
        ligne.setCamion(mission.getCamion());
        ligne.setDesignation(mission.getTypeMission().getLibelle() + " — mission " + mission.getNumero()
                + " " + trajet(mission));
        ligne.setQuantite(BigDecimal.ONE);
        ligne.setPrixUnitaire(montant);
        ligne.setUnite("forfait");
        facture.ajouterLigne(ligne);
        facture.recalculer();

        return FactureDto.Reponse.complete(repository.save(facture));
    }

    /**
     * Emet la facture : elle devient exigible et une recette est enregistree
     * pour chaque camion facture (chapitre 15).
     */
    public FactureDto.Reponse emettre(Long id) {
        Facture facture = charger(id);
        if (facture.getStatut() != StatutFacture.BROUILLON) {
            throw new BusinessException("Seule une facture en brouillon peut etre emise");
        }
        if (facture.getLignes().isEmpty()) {
            throw new BusinessException("Une facture sans ligne ne peut pas etre emise");
        }
        facture.recalculer();
        facture.setStatut(StatutFacture.NON_PAYEE);
        if (facture.getDateEcheance() == null) {
            int delai = facture.getClient().getDelaiPaiementJours() == null
                    ? DELAI_PAIEMENT_DEFAUT
                    : facture.getClient().getDelaiPaiementJours();
            facture.setDateEcheance(facture.getDateEmission().plusDays(delai));
        }
        enregistrerRecettes(facture);
        return FactureDto.Reponse.complete(repository.save(facture));
    }

    /**
     * Facture emise pour une recette qui vient d'etre enregistree.
     * <p>
     * La recette est rattachee a la facture <em>avant</em> l'emission : sans ce
     * rattachement, l'emission creerait une seconde recette pour la meme
     * prestation et le camion serait credite deux fois.
     *
     * @return la facture emise, ou null si la recette n'a pas de client a qui
     *         l'adresser
     */
    public Facture facturerRecette(Recette recette) {
        if (recette.getClient() == null) {
            return null;
        }

        Facture facture = nouvelleFacture(recette.getClient(), recette.getDateRecette(), null);
        facture.setLocation(recette.getLocation());
        facture.setMission(recette.getMission());

        FactureLigne ligne = new FactureLigne();
        ligne.setCamion(recette.getCamion());
        ligne.setDesignation(recette.getLibelle());
        ligne.setQuantite(BigDecimal.ONE);
        ligne.setUnite("forfait");
        ligne.setPrixUnitaire(recette.getMontant());
        facture.ajouterLigne(ligne);
        facture.recalculer();
        facture = repository.save(facture);

        recette.setFacture(facture);
        recetteRepository.save(recette);

        facture.setStatut(StatutFacture.NON_PAYEE);
        return repository.save(facture);
    }

    /** Enregistre un reglement et met a jour le statut de la facture. */
    public FactureDto.Reponse enregistrerPaiement(Long id, FactureDto.PaiementRequete requete) {
        Facture facture = charger(id);
        if (facture.getStatut() == StatutFacture.BROUILLON) {
            throw new BusinessException("Emettez la facture avant d'enregistrer un reglement");
        }
        if (facture.getStatut() == StatutFacture.ANNULEE) {
            throw new BusinessException("Cette facture est annulee");
        }
        BigDecimal reste = facture.resteAPayer();
        if (requete.montant().compareTo(reste) > 0) {
            throw new BusinessException("Le reglement (" + requete.montant()
                    + ") depasse le reste a payer (" + reste + ")");
        }

        Paiement paiement = new Paiement();
        paiement.setFacture(facture);
        paiement.setMontant(requete.montant());
        paiement.setDatePaiement(requete.datePaiement());
        paiement.setModePaiement(requete.modePaiement());
        paiement.setReference(requete.reference());
        paiement.setObservations(requete.observations());
        paiementRepository.save(paiement);

        facture.getPaiements().add(paiement);
        facture.reevaluerStatut();
        return FactureDto.Reponse.complete(repository.save(facture));
    }

    public FactureDto.Reponse annuler(Long id, String motif) {
        Facture facture = charger(id);
        if (facture.getStatut() == StatutFacture.ANNULEE) {
            throw new BusinessException("Cette facture est deja annulee");
        }
        if (facture.montantPaye().signum() > 0) {
            throw new BusinessException("Cette facture a deja recu un reglement : elle ne peut pas etre annulee");
        }
        facture.setStatut(StatutFacture.ANNULEE);
        if (motif != null && !motif.isBlank()) {
            facture.setObservations(motif);
        }
        return FactureDto.Reponse.complete(repository.save(facture));
    }

    /**
     * Bascule en retard les factures dont l'echeance est depassee.
     * Appelee a la consultation des creances ; un ordonnanceur pourra la declencher.
     */
    public int marquerRetards() {
        List<Facture> echues = repository.echuesNonSoldees(LocalDate.now());
        echues.forEach(f -> f.setStatut(StatutFacture.EN_RETARD));
        repository.saveAll(echues);
        return echues.size();
    }

    @Transactional(readOnly = true)
    public Facture charger(Long id) {
        return repository.chargerAvecLignes(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Facture", id));
    }

    private Facture nouvelleFacture(Client client, LocalDate dateEmission, Integer delaiPaiement) {
        Facture facture = new Facture();
        facture.setNumero(numerotation.prochainNumero("FAC"));
        facture.setClient(client);
        facture.setDateEmission(dateEmission == null ? LocalDate.now() : dateEmission);
        int delai = delaiPaiement != null ? delaiPaiement
                : client.getDelaiPaiementJours() != null ? client.getDelaiPaiementJours()
                : DELAI_PAIEMENT_DEFAUT;
        facture.setDateEcheance(facture.getDateEmission().plusDays(delai));
        // Le taux vient du parametrage de l'entreprise, et y reste fige sur la facture.
        facture.setTauxTva(societeService.tauxTva());
        return facture;
    }

    /**
     * Une recette par camion facture : c'est ce rattachement qui alimente le
     * calcul de rentabilite par vehicule.
     */
    private void enregistrerRecettes(Facture facture) {
        if (recetteRepository.existsByFactureId(facture.getId())) {
            return;
        }
        for (FactureLigne ligne : facture.getLignes()) {
            if (ligne.getCamion() == null) {
                continue;
            }
            Recette recette = new Recette();
            recette.setNumero(numerotation.prochainNumero("REC"));
            recette.setCamion(ligne.getCamion());
            recette.setClient(facture.getClient());
            recette.setFacture(facture);
            recette.setLocation(facture.getLocation());
            recette.setMission(facture.getMission());
            recette.setType(typeDe(facture));
            recette.setLibelle(ligne.getDesignation());
            recette.setMontant(ligne.getMontant());
            recette.setDateRecette(facture.getDateEmission());
            recette.setReference(facture.getNumero());
            recetteRepository.save(recette);
        }
    }

    private TypeRecette typeDe(Facture facture) {
        if (facture.getLocation() != null) {
            return TypeRecette.LOCATION;
        }
        return facture.getMission() != null ? TypeRecette.TRANSPORT : TypeRecette.AUTRE;
    }

    private String trajet(Mission mission) {
        if (mission.getLieuDepart() == null && mission.getDestination() == null) {
            return "";
        }
        return "(" + (mission.getLieuDepart() == null ? "?" : mission.getLieuDepart())
                + " - " + (mission.getDestination() == null ? "?" : mission.getDestination()) + ")";
    }

    private void appliquer(Facture f, FactureDto.Requete r) {
        f.setClient(clientService.charger(r.clientId()));
        f.setLocation(r.locationId() == null ? null : locationService.charger(r.locationId()));
        f.setMission(r.missionId() == null ? null : missionService.charger(r.missionId()));
        f.setDateEmission(r.dateEmission());
        f.setDateEcheance(r.dateEcheance());
        f.setPeriodeDebut(r.periodeDebut());
        f.setPeriodeFin(r.periodeFin());
        f.setTauxTva(societeService.tauxTva());
        f.setObservations(r.observations());

        for (FactureDto.LigneRequete ligneRequete : r.lignes()) {
            FactureLigne ligne = new FactureLigne();
            ligne.setCamion(ligneRequete.camionId() == null
                    ? null : camionService.charger(ligneRequete.camionId()));
            ligne.setDesignation(ligneRequete.designation().trim());
            ligne.setQuantite(ligneRequete.quantite());
            ligne.setUnite(ligneRequete.unite());
            ligne.setPrixUnitaire(ligneRequete.prixUnitaire());
            f.ajouterLigne(ligne);
        }
        f.recalculer();
    }
}
