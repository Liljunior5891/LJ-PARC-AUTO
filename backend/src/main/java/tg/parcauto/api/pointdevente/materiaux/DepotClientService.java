package tg.parcauto.api.pointdevente.materiaux;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.caisse.CaisseService;
import tg.parcauto.api.pointdevente.caisse.MouvementCaisse;
import tg.parcauto.api.pointdevente.caisse.SessionCaisse;
import tg.parcauto.api.pointdevente.caisse.SessionTravailService;
import tg.parcauto.api.pointdevente.vente.ModePaiementPdv;
import tg.parcauto.api.pointdevente.vente.Vente;
import tg.parcauto.api.pointdevente.vente.VenteRepository;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Depot d'argent des clients de la quincaillerie.
 * <p>
 * Le client verse de l'argent sans rien acheter, sur la session de caisse du
 * poste (especes, mobile money, carte) ; un recu de depot lui est remis. A la
 * caisse, son solde paie ensuite tout ou partie d'un achat. Le solde non utilise
 * peut lui etre rembourse : le caissier le demande, le gerant valide ; par la
 * caisse, les especes sortent alors de la session ouverte, sinon par la banque.
 * <p>
 * Solde = depots valides - achats et versements payes par le compte client -
 * remboursements valides ou en attente (une demande en attente bloque deja son montant).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DepotClientService {

    private static final Set<DepotClient.Statut> VALIDES = Set.of(DepotClient.Statut.VALIDE);

    private final DepotClientRepository repository;
    private final ClientPdvRepository clients;
    private final VenteRepository ventes;
    private final ReglageMateriauxService reglages;
    private final SessionTravailService sessionsTravail;
    private final CaisseService caisses;
    private final NumerotationService numerotation;
    private final tg.parcauto.api.pointdevente.caisse.PerimetreCaisse perimetre;
    /** Versements de ventes a credit payes avec le compte client. */
    private final ReglementClientRepository reglements;

    // ------------------------------------------------------------------ dto

    public record DepotRequete(@NotNull @Positive BigDecimal montant, @NotNull ModePaiementPdv modePaiement,
                               @PositiveOrZero BigDecimal montantRecu, @Size(max = 80) String referencePaiement,
                               Long sessionId, @Size(max = 500) String motif) {
    }

    public record RemboursementRequete(@NotNull @Positive BigDecimal montant, @NotNull DepotClient.Source source,
                                       Long sessionId, @Size(max = 500) String motif) {
    }

    public record Refus(@jakarta.validation.constraints.NotBlank @Size(max = 500) String motif) {
    }

    /** Operation du compte de depot, telle qu'affichee et imprimee. */
    public record Operation(Long id, String numero, DepotClient.Type type, Long clientId, String clientNom,
                            String clientTelephone, BigDecimal montant, ModePaiementPdv modePaiement,
                            String modePaiementLibelle, DepotClient.Source source, String sessionNumero,
                            BigDecimal montantRecu, BigDecimal monnaieRendue, String referencePaiement, String motif,
                            DepotClient.Statut statut, String auteurNom, Instant date, String traitePar,
                            Instant traiteLe, String motifRefus,
                            /** Solde du client apres l'operation (a l'impression). */
                            BigDecimal soldeApres) {
    }

    /** Achat paye avec le depot. */
    public record Achat(Long venteId, String numero, Instant date, BigDecimal total, BigDecimal payeParDepot) {
    }

    public record CompteDepot(Long clientId, String clientNom, BigDecimal solde, BigDecimal deposes,
                              BigDecimal utilises, BigDecimal rembourses, BigDecimal enAttente,
                              List<Operation> operations, List<Achat> achats) {
    }

    // --------------------------------------------------------------- soldes

    /** Solde de depot de chaque client demande (absent : zero). */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> soldes(Collection<Long> ids) {
        Map<Long, BigDecimal> soldes = new HashMap<>();
        if (ids.isEmpty()) {
            return soldes;
        }
        for (Object[] l : repository.mouvementsNets(ids)) {
            soldes.merge((Long) l[0], (BigDecimal) l[1], BigDecimal::add);
        }
        for (Object[] l : ventes.payesParDepot(ids)) {
            soldes.merge((Long) l[0], ((BigDecimal) l[1]).negate(), BigDecimal::add);
        }
        for (Object[] l : reglements.payesParCompte(ids)) {
            soldes.merge((Long) l[0], ((BigDecimal) l[1]).negate(), BigDecimal::add);
        }
        return soldes;
    }

    @Transactional(readOnly = true)
    public BigDecimal solde(Long clientId) {
        return soldes(List.of(clientId)).getOrDefault(clientId, BigDecimal.ZERO);
    }

    /**
     * Verrouille la fiche du client et exige que son depot couvre le montant :
     * deux achats ou remboursements simultanes ne depassent pas a eux deux le solde.
     */
    public ClientPdv exigerSolde(Long clientId, BigDecimal montant) {
        ClientPdv client = clients.verrouiller(clientId).orElseThrow(() -> ResourceNotFoundException.of("Client", clientId));
        BigDecimal solde = solde(clientId);
        if (montant.compareTo(solde) > 0) {
            throw new BusinessException("Le depot de " + client.getNom() + " ne contient que " + entier(solde.max(BigDecimal.ZERO))
                    + " F : " + entier(montant) + " F demandes");
        }
        return client;
    }

    // --------------------------------------------------------------- compte

    @Transactional(readOnly = true)
    public CompteDepot compte(Long clientId) {
        reglages.exiger();
        ClientPdv client = clients.findById(clientId).orElseThrow(() -> ResourceNotFoundException.of("Client", clientId));
        List<DepotClient> liste = repository.findByClientIdOrderByDateOperationDesc(clientId);
        BigDecimal deposes = repository.total(clientId, DepotClient.Type.DEPOT, VALIDES);
        BigDecimal rembourses = repository.total(clientId, DepotClient.Type.REMBOURSEMENT, VALIDES);
        BigDecimal attente = repository.total(clientId, DepotClient.Type.REMBOURSEMENT, Set.of(DepotClient.Statut.EN_ATTENTE));
        List<Achat> achats = new ArrayList<>(ventes.achatsParDepot(clientId).stream()
                .map(v -> new Achat(v.getId(), v.getNumero(), v.getDateVente(), v.getTotal(), v.getPayeParDepot())).toList());
        // Versements sur des ventes a credit payes avec le compte : une ligne par vente reglee.
        for (ReglementClient r : reglements.versementsParCompte(clientId)) {
            achats.add(new Achat(r.getVente().getId(), r.getVente().getNumero() + " (versement " + r.getNumero() + ")",
                    r.getDateReglement(), r.getVente().getTotal(), r.getMontant()));
        }
        achats.sort(Comparator.comparing(Achat::date).reversed());
        BigDecimal utilises = achats.stream().map(Achat::payeParDepot).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Le solde affiche est celui que controle exigerSolde : une seule formule.
        return new CompteDepot(clientId, client.getNom(), solde(clientId),
                deposes, utilises, rembourses, attente, liste.stream().map(d -> operation(d, null)).toList(), achats);
    }

    /** Remboursements qui attendent le gerant (validations et notifications). */
    @Transactional(readOnly = true)
    public List<Operation> remboursementsEnAttente() {
        if (!reglages.estMateriaux()) {
            return List.of();
        }
        return repository.enAttenteAvecClient(DepotClient.Type.REMBOURSEMENT, DepotClient.Statut.EN_ATTENTE)
                .stream().map(d -> operation(d, null)).toList();
    }

    // ---------------------------------------------------------------- depot

    /** Le client depose de l'argent sur la session de caisse du poste. */
    public Operation deposer(Long clientId, DepotRequete r, UtilisateurPrincipal caissier) {
        reglages.exiger();
        ClientPdv client = clients.verrouiller(clientId).orElseThrow(() -> ResourceNotFoundException.of("Client", clientId));
        if (!client.isActif()) {
            throw new BusinessException("Le client " + client.getNom() + " est archive");
        }
        SessionCaisse session = sessionsTravail.exiger(caissier, r.sessionId());
        BigDecimal montant = montant(r.montant());
        if (r.modePaiement() == ModePaiementPdv.COMPTE_CLIENT) {
            throw new BusinessException("Un depot se fait en especes, par mobile money ou par carte");
        }
        if ((r.modePaiement() == ModePaiementPdv.MOBILE_MONEY || r.modePaiement() == ModePaiementPdv.CARTE)
                && vide(r.referencePaiement()) == null) {
            throw new BusinessException("Indiquez la reference du paiement " + r.modePaiement().getLibelle().toLowerCase()
                    + " (numero de transaction)");
        }
        BigDecimal recu = montant;
        if (r.modePaiement() == ModePaiementPdv.ESPECES && r.montantRecu() != null) {
            recu = montant(r.montantRecu());
            if (recu.compareTo(montant) < 0) {
                throw new BusinessException("Montant recu insuffisant : il manque " + entier(montant.subtract(recu)) + " F");
            }
        }
        DepotClient d = new DepotClient();
        d.setNumero(numerotation.prochainNumero("DEP"));
        d.setClient(client);
        d.setType(DepotClient.Type.DEPOT);
        d.setMontant(montant);
        d.setModePaiement(r.modePaiement());
        d.setSource(DepotClient.Source.CAISSE);
        d.setSession(session);
        d.setMontantRecu(recu);
        d.setReferencePaiement(vide(r.referencePaiement()));
        d.setMotif(vide(r.motif()));
        d.setStatut(DepotClient.Statut.VALIDE);
        d.setAuteurId(caissier.getId());
        d.setAuteurNom(caissier.getNomComplet());
        d.setDateOperation(Instant.now());
        repository.save(d);
        return operation(d, solde(clientId));
    }

    /**
     * Annulation d'un depot saisi par erreur, par un gerant, tant que sa session est
     * ouverte et que le solde du client le permet (rien n'a encore ete depense).
     */
    public Operation annulerDepot(String numero, String motif, UtilisateurPrincipal gerant) {
        reglages.exiger();
        DepotClient d = parNumero(numero);
        if (d.getType() != DepotClient.Type.DEPOT || d.getStatut() != DepotClient.Statut.VALIDE) {
            throw new BusinessException("Le depot " + numero + " ne peut pas etre annule");
        }
        if (!d.getSession().ouverte()) {
            throw new BusinessException("La session " + d.getSession().getNumero() + " est fermee : ce depot ne peut plus etre annule");
        }
        exigerSolde(d.getClient().getId(), d.getMontant());
        d.setStatut(DepotClient.Statut.ANNULE);
        d.setTraitePar(gerant.getNomComplet());
        d.setTraiteLe(Instant.now());
        d.setMotifRefus(vide(motif));
        return operation(d, solde(d.getClient().getId()));
    }

    // -------------------------------------------------------- remboursement

    /**
     * Demande de remboursement du solde : le montant est bloque des la demande.
     * Faite par un gerant ou un administrateur, elle est validee aussitot.
     */
    public Operation demanderRemboursement(Long clientId, RemboursementRequete r, UtilisateurPrincipal auteur) {
        reglages.exiger();
        BigDecimal montant = montant(r.montant());
        ClientPdv client = exigerSolde(clientId, montant);
        DepotClient d = new DepotClient();
        d.setNumero(numerotation.prochainNumero("RBD"));
        d.setClient(client);
        d.setType(DepotClient.Type.REMBOURSEMENT);
        d.setMontant(montant);
        d.setSource(r.source());
        if (r.source() == DepotClient.Source.CAISSE) {
            d.setSession(sessionsTravail.exiger(auteur, r.sessionId()));
            d.setModePaiement(ModePaiementPdv.ESPECES);
        }
        d.setMotif(vide(r.motif()));
        d.setStatut(DepotClient.Statut.EN_ATTENTE);
        d.setAuteurId(auteur.getId());
        d.setAuteurNom(auteur.getNomComplet());
        d.setDateOperation(Instant.now());
        repository.save(d);
        if (CompteClientService.estGestionnaire(auteur)) {
            valider(d, auteur);
        }
        return operation(d, solde(clientId));
    }

    /** Le gerant valide : par la caisse, les especes sortent de la session (qui doit les contenir). */
    public Operation validerRemboursement(Long id, UtilisateurPrincipal gerant) {
        reglages.exiger();
        DepotClient d = enAttente(id);
        valider(d, gerant);
        return operation(d, solde(d.getClient().getId()));
    }

    public Operation refuserRemboursement(Long id, String motif, UtilisateurPrincipal gerant) {
        reglages.exiger();
        DepotClient d = enAttente(id);
        d.setStatut(DepotClient.Statut.REFUSE);
        d.setTraitePar(gerant.getNomComplet());
        d.setTraiteLe(Instant.now());
        d.setMotifRefus(motif.trim());
        return operation(d, solde(d.getClient().getId()));
    }

    // ----------------------------------------------------------------- recu

    @Transactional(readOnly = true)
    public Operation recu(String numero, UtilisateurPrincipal lecteur) {
        reglages.exiger();
        DepotClient d = parNumero(numero);
        if (d.getSession() != null) {
            // Chaque caisse ne voit que ses operations.
            perimetre.exiger(d.getSession(), lecteur);
        }
        return operation(d, solde(d.getClient().getId()));
    }

    // ------------------------------------------------------------- internes

    private void valider(DepotClient d, UtilisateurPrincipal gerant) {
        if (d.getSource() == DepotClient.Source.CAISSE) {
            SessionCaisse session = d.getSession();
            if (!session.ouverte()) {
                throw new BusinessException("La session " + session.getNumero()
                        + " est fermee : refusez la demande et remboursez par la banque ou depuis une caisse ouverte");
            }
            BigDecimal tiroir = caisses.especesDuTiroir(session.getId());
            if (d.getMontant().compareTo(tiroir) > 0) {
                throw new BusinessException("La caisse " + session.getCaisse().getNom() + " ne contient que "
                        + entier(tiroir) + " F d'especes");
            }
            MouvementCaisse m = caisses.retraitRemise(session.getId(), d.getMontant(),
                    "Remboursement du depot de " + d.getClient().getNom() + " (" + d.getNumero() + ")", gerant);
            d.setMouvementId(m.getId());
        }
        d.setStatut(DepotClient.Statut.VALIDE);
        d.setTraitePar(gerant.getNomComplet());
        d.setTraiteLe(Instant.now());
    }

    private DepotClient enAttente(Long id) {
        DepotClient d = repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Remboursement", id));
        if (d.getType() != DepotClient.Type.REMBOURSEMENT || d.getStatut() != DepotClient.Statut.EN_ATTENTE) {
            throw new BusinessException("Le remboursement " + d.getNumero() + " n'attend pas de decision");
        }
        return d;
    }

    private DepotClient parNumero(String numero) {
        return repository.findByNumero(numero).orElseThrow(() -> new ResourceNotFoundException("Operation introuvable : " + numero));
    }

    private static Operation operation(DepotClient d, BigDecimal soldeApres) {
        BigDecimal recu = d.getMontantRecu();
        return new Operation(d.getId(), d.getNumero(), d.getType(), d.getClient().getId(), d.getClient().getNom(),
                d.getClient().getTelephone(), d.getMontant(), d.getModePaiement(),
                d.getModePaiement() == null ? "Banque" : d.getModePaiement().getLibelle(), d.getSource(),
                d.getSession() == null ? null : d.getSession().getNumero(), recu,
                recu == null ? BigDecimal.ZERO : recu.subtract(d.getMontant()).max(BigDecimal.ZERO),
                d.getReferencePaiement(), d.getMotif(), d.getStatut(), d.getAuteurNom(), d.getDateOperation(),
                d.getTraitePar(), d.getTraiteLe(), d.getMotifRefus(), soldeApres);
    }

    private static BigDecimal montant(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static String entier(BigDecimal v) {
        return String.format(java.util.Locale.FRENCH, "%,.0f", v).replace(' ', ' ').replace(' ', ' ');
    }
}
