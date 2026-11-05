package tg.parcauto.api.marketplace;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.finance.recette.TypeRecette;
import tg.parcauto.api.exploitation.location.LocationRepository;
import tg.parcauto.api.parc.camion.Camion;
import tg.parcauto.api.parc.camion.CamionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Demandes de location d'un vehicule faites depuis la boutique publique.
 * Acceptee, une demande reserve le vehicule sur ses dates ; le client se
 * presente ensuite en agence, ou l'on enregistre la recette (type au choix)
 * et sa fiche client complete, ce qui conclut la demande. Sans visite avant la
 * date de debut, elle expire et libere le vehicule.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DemandeLocationService {

    /** Au-dela, la demande releve d'un contrat longue duree negocie de vive voix. */
    private static final int JOURS_MAX = 90;

    private final DemandeLocationRepository repository;
    private final OffreRepository offreRepository;
    private final OffreService offreService;
    private final BoutiqueService boutiqueService;
    private final CamionRepository camionRepository;
    private final LocationRepository locationRepository;
    private final NumerotationService numerotation;
    private final tg.parcauto.api.finance.recette.DisponibiliteVehiculeService disponibilite;
    private final tg.parcauto.api.finance.recette.RecetteRepository recetteRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public MarketplaceDto.Suivi demander(MarketplaceDto.DemandeLocationPublique d) {
        Boutique boutique = boutiqueService.publiee();
        Offre offre = offreRepository.findById(d.offreId())
                .filter(o -> o.isPubliee() && o.getType() == Offre.Type.VEHICULE)
                .orElseThrow(() -> new BusinessException("Ce vehicule n'est plus propose a la location"));
        if (!offreService.disponible(offre)) {
            throw new BusinessException("Ce vehicule n'est pas disponible a la location pour le moment");
        }
        if (d.dateDebut().isBefore(LocalDate.now())) {
            throw new BusinessException("La location ne peut pas commencer dans le passe");
        }
        if (d.dateFin().isBefore(d.dateDebut())) {
            throw new BusinessException("La date de fin precede la date de debut");
        }
        long jours = jours(d.dateDebut(), d.dateFin());
        if (jours > JOURS_MAX) {
            throw new BusinessException("Pour plus de " + JOURS_MAX + " jours, contactez directement la boutique");
        }
        // Les engagements internes (contrats, missions, reservations) ne sont pas detailles au public.
        if (!disponibilite.occupations(offre.getCamionId(), TypeRecette.LOCATION, d.dateDebut(), d.dateFin(),
                null, null, null).isEmpty()) {
            throw new BusinessException("Ce vehicule est deja reserve sur une partie de ces dates");
        }

        DemandeLocation demande = new DemandeLocation();
        demande.setNumero(numerotation.prochainNumero("DLC"));
        demande.setJeton(ClientEnLigneService.nouveauJeton());
        demande.setDateDemande(Instant.now());
        demande.setOffreId(offre.getId());
        demande.setCamionId(offre.getCamionId());
        demande.setClientNom(MarketplaceDto.nomComplet(d.nom(), d.prenoms()));
        demande.setClientNomFamille(d.nom().trim());
        demande.setClientPrenoms(MarketplaceDto.prenoms(d.prenoms()));
        demande.setClientTelephone(d.telephone().trim());
        demande.setClientEmail(vide(d.email()));
        demande.setDateDebut(d.dateDebut());
        demande.setDateFin(d.dateFin());
        demande.setTarifJour(offre.getPrix());
        demande.setMontantEstime(offre.getPrix().multiply(BigDecimal.valueOf(jours)));
        demande.setLieuDepart(vide(d.lieuDepart()));
        demande.setDestination(vide(d.destination()));
        demande.setMessage(vide(d.message()));
        // Pas de fiche client a la demande : elle est creee (ou completee) en agence, avec ses pieces.
        return suivi(boutique, repository.save(demande));
    }

    public MarketplaceDto.Suivi suivi(String jeton) {
        expirer();
        return suivi(boutiqueService.courante(), parJeton(jeton));
    }

    public MarketplaceDto.DemandeLocationReponse obtenir(Long id) {
        expirer();
        return versReponse(repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Demande de location", id)));
    }

    public Page<MarketplaceDto.DemandeLocationReponse> lister(DemandeLocation.Statut statut, Pageable pageable) {
        expirer();
        Page<DemandeLocation> page = statut == null
                ? repository.findAllByOrderByDateDemandeDesc(pageable)
                : repository.findByStatutOrderByDateDemandeDesc(statut, pageable);
        return page.map(this::versReponse);
    }

    /**
     * Accepte la demande : le vehicule est reserve sur ses dates en attendant le
     * client, qui se presente en agence ou l'on enregistre la recette. Aucun
     * contrat n'est cree a ce stade. Le tarif par jour peut etre ajuste.
     */
    public MarketplaceDto.DemandeLocationReponse accepter(Long id, MarketplaceDto.Acceptation acceptation) {
        DemandeLocation demande = nouvelle(id);
        disponibilite.exiger(demande.getCamionId(), TypeRecette.LOCATION, demande.getDateDebut(),
                demande.getDateFin(), null, null, null, demande.getId());
        BigDecimal tarif = acceptation.tarifJour() != null ? acceptation.tarifJour() : demande.getTarifJour();
        demande.setTarifJour(tarif);
        demande.setMontantEstime(tarif.multiply(BigDecimal.valueOf(jours(demande.getDateDebut(), demande.getDateFin()))));
        demande.setStatut(DemandeLocation.Statut.ACCEPTEE);
        return versReponse(repository.save(demande));
    }

    /** Annule une demande acceptee (le client se desiste) : le vehicule est libere. */
    public MarketplaceDto.DemandeLocationReponse annuler(Long id, MarketplaceDto.Refus refus) {
        DemandeLocation demande = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Demande de location", id));
        if (demande.getStatut() != DemandeLocation.Statut.ACCEPTEE) {
            throw new BusinessException("Seule une demande acceptee, en attente du client, peut etre annulee");
        }
        demande.setStatut(DemandeLocation.Statut.ANNULEE);
        demande.setMotifRefus(refus == null ? null : vide(refus.motif()));
        return versReponse(repository.save(demande));
    }

    /**
     * Recette enregistree en agence pour la demande : elle la conclut. Refusee
     * si la demande n'attend plus le client (expiree, annulee, deja conclue).
     */
    @org.springframework.context.event.EventListener
    public void recetteEnregistree(tg.parcauto.api.finance.recette.RecetteDto.RecetteDepuisDemande evenement) {
        expirer();
        DemandeLocation demande = repository.findById(evenement.demandeId())
                .orElseThrow(() -> ResourceNotFoundException.of("Demande de location", evenement.demandeId()));
        if (demande.getStatut() != DemandeLocation.Statut.ACCEPTEE) {
            throw new BusinessException("La demande " + demande.getNumero() + " est "
                    + demande.getStatut().getLibelle().toLowerCase() + " : elle ne peut plus etre conclue");
        }
        demande.setStatut(DemandeLocation.Statut.CONCLUE);
        demande.setRecetteId(evenement.recetteId());
        repository.save(demande);
    }

    /**
     * Expiration : une demande acceptee dont la date de debut est passee sans que
     * le client soit venu libere le vehicule. Toutes entreprises confondues, en
     * SQL direct ; lancee chaque heure et avant chaque lecture des demandes.
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 5 * * * *")
    public void expirer() {
        jdbc.update("update mkt_demande_location set statut = 'EXPIREE', modifie_le = now() "
                + "where statut = 'ACCEPTEE' and date_debut < current_date");
    }

    public MarketplaceDto.DemandeLocationReponse refuser(Long id, MarketplaceDto.Refus refus) {
        DemandeLocation demande = nouvelle(id);
        demande.setStatut(DemandeLocation.Statut.REFUSEE);
        demande.setMotifRefus(refus == null ? null : vide(refus.motif()));
        return versReponse(repository.save(demande));
    }

    @Transactional(readOnly = true)
    public long nouvelles() {
        return repository.countByStatut(DemandeLocation.Statut.NOUVELLE);
    }

    // ------------------------------------------------------------- Interne

    private DemandeLocation nouvelle(Long id) {
        DemandeLocation demande = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Demande de location", id));
        if (demande.getStatut() != DemandeLocation.Statut.NOUVELLE) {
            throw new BusinessException("Cette demande a deja recu une reponse");
        }
        return demande;
    }

    private MarketplaceDto.Suivi suivi(Boutique boutique, DemandeLocation demande) {
        return new MarketplaceDto.Suivi("LOCATION", boutique.getNom(), boutique.getSlug(), boutique.getTelephone(),
                boutique.getWhatsapp(), boutique.getMessageConfirmation(), null, versReponse(demande), null, false);
    }

    private MarketplaceDto.DemandeLocationReponse versReponse(DemandeLocation d) {
        Camion camion = camionRepository.findById(d.getCamionId()).orElse(null);
        String vehicule = camion == null ? "Vehicule retire du parc"
                : OffreService.libelleVehicule(camion) + " (" + camion.getImmatriculation() + ")";
        String contrat = d.getLocationId() == null ? null
                : locationRepository.findById(d.getLocationId()).map(l -> l.getNumero()).orElse(null);
        return new MarketplaceDto.DemandeLocationReponse(d.getId(), d.getNumero(), d.getJeton(), d.getOffreId(),
                d.getCamionId(), vehicule, d.getClientId(), d.getClientNom(), d.getClientTelephone(),
                d.getClientEmail(), d.getDateDebut(), d.getDateFin(), jours(d.getDateDebut(), d.getDateFin()),
                d.getTarifJour(), d.getMontantEstime(), d.getLieuDepart(), d.getDestination(), d.getMessage(),
                d.getStatut(), d.getStatut().getLibelle(), d.getMotifRefus(), d.getLocationId(), contrat,
                d.getDateDemande(), d.getRecetteId(),
                d.getRecetteId() == null ? null : recetteRepository.findById(d.getRecetteId()).map(r -> r.getNumero()).orElse(null),
                d.getOffreId() == null ? null : offreRepository.findById(d.getOffreId()).map(Offre::getCaution).orElse(null),
                d.getClientNomFamille(), d.getClientPrenoms());
    }

    private DemandeLocation parJeton(String jeton) {
        return repository.findByJeton(jeton).orElseThrow(() -> new ResourceNotFoundException("Demande introuvable"));
    }

    /** Duree facturee : le jour de depart et le jour de retour comptent tous les deux. */
    static long jours(LocalDate debut, LocalDate fin) {
        return ChronoUnit.DAYS.between(debut, fin) + 1;
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }
}
