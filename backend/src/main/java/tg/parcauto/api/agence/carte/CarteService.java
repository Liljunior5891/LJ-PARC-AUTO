package tg.parcauto.api.agence.carte;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.agence.Agence;
import tg.parcauto.api.agence.AuditAgenceService;
import tg.parcauto.api.agence.DroitsAgence;
import tg.parcauto.api.agence.InitialisationAgence;
import tg.parcauto.api.agence.PerimetreAgence;
import tg.parcauto.api.agence.PermissionAgence;
import tg.parcauto.api.agence.caisse.SessionCaisseService;
import tg.parcauto.api.agence.cloture.VerrouJournee;
import tg.parcauto.api.agence.operation.TransactionAgence;
import tg.parcauto.api.agence.tresorerie.CompteAgence;
import tg.parcauto.api.agence.tresorerie.CompteAgenceRepository;
import tg.parcauto.api.agence.tresorerie.CompteService;
import tg.parcauto.api.agence.tresorerie.Ecriture;
import tg.parcauto.api.agence.tresorerie.NatureEcriture;
import tg.parcauto.api.agence.tresorerie.RegistreService;
import tg.parcauto.api.agence.tresorerie.TypeCompte;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cartes prepayees : stock par lot au cout reel, vente (lots les plus anciens
 * d'abord, prix encaisse en caisse, marge sur le cout reel) et suivi du compte
 * de recharge. La recharge elle-meme est une transaction du service des cartes.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CarteService {

    private final ProduitCarteRepository produits;
    private final LotCarteRepository lots;
    private final VenteCarteRepository ventes;
    private final PerimetreAgence perimetre;
    private final DroitsAgence droits;
    private final CompteAgenceRepository comptes;
    private final CompteService compteService;
    private final RegistreService registre;
    private final SessionCaisseService sessions;
    private final VerrouJournee verrou;
    private final NumerotationService numerotation;
    private final InitialisationAgence initialisation;
    private final AuditAgenceService audit;

    @PersistenceContext
    private EntityManager em;

    public record ProduitRequete(@NotBlank @Size(max = 120) String nom, @NotNull @Positive Long prixVente,
                                 @PositiveOrZero Integer seuilAlerte, Boolean actif) {
    }

    public record ProduitDto(Long id, String nom, long prixVente, int seuilAlerte, boolean actif) {
    }

    public record Entree(@NotNull Long agenceId, @NotNull Long produitId, @NotNull @Min(1) Integer quantite,
                         @NotNull @PositiveOrZero Long coutUnitaire, Long compteId, @Size(max = 150) String fournisseur,
                         @Size(max = 80) String reference) {
    }

    public record LotDto(Long id, String numero, Long agenceId, Long produitId, Instant dateEntree, int quantiteInitiale,
                         int quantiteRestante, long coutUnitaire, String fournisseur, String reference, String compteNom,
                         String auteurNom) {
    }

    public record StockProduit(Long produitId, String nom, long prixVente, int disponible, long valeurStock, int seuilAlerte,
                               boolean alerte, long margeUnitaireEstimee, List<LotDto> lots) {
    }

    public record Vente(@NotNull Long agenceId, @NotNull Long produitId, @NotNull @Min(1) Integer quantite,
                        @Size(max = 150) String clientNom, @Size(max = 30) String clientTelephone) {
    }

    public record VenteDto(Long id, String reference, Long agenceId, String agenceNom, Long produitId, String produitNom,
                           Instant dateVente, LocalDate journee, int quantite, long prixUnitaire, long montant, long coutTotal,
                           long marge, String compteNom, String clientNom, String clientTelephone, String caissierNom,
                           VenteCarte.Statut statut, Instant annuleeLe, String annuleeParNom, String motifAnnulation,
                           boolean annulable) {
    }

    public record TotauxVentes(long ventes, long cartes, long chiffreAffaires, long cout, long marge) {
    }

    public record SuiviRecharge(long soldeOuverture, long soldeActuel, long approvisionnements, long nombreRecharges,
                                long montantRecharges, long commissions) {
    }

    public record Suivi(TotauxVentes ventes, SuiviRecharge recharge) {
    }

    // ---------------------------------------------------------------- produits

    public List<ProduitDto> produits() {
        initialisation.assurer();
        if (produits.count() == 0) {
            ProduitCarte p = new ProduitCarte();
            p.setNom("Carte Xpress");
            p.setPrixVente(5500);
            p.setSeuilAlerte(2);
            produits.save(p);
        }
        return produits.findAllByOrderByNomAsc().stream().map(CarteService::versDto).toList();
    }

    public ProduitDto enregistrerProduit(Long id, ProduitRequete r) {
        String nom = r.nom().trim();
        if (id == null ? produits.existsByNomIgnoreCase(nom) : produits.existsByNomIgnoreCaseAndIdNot(nom, id)) {
            throw new BusinessException("La carte " + nom + " existe deja");
        }
        ProduitCarte p = id == null ? new ProduitCarte() : produit(id);
        ProduitDto avant = id == null ? null : versDto(p);
        p.setNom(nom);
        p.setPrixVente(r.prixVente());
        if (r.seuilAlerte() != null) {
            p.setSeuilAlerte(r.seuilAlerte());
        }
        if (r.actif() != null) {
            p.setActif(r.actif());
        }
        p = produits.save(p);
        audit.tracer(null, id == null ? "CREATION" : "MODIFICATION", "PRODUIT_CARTE", p.getId(), "Carte " + p.getNom()
                + " : prix de vente " + RegistreService.francs(p.getPrixVente()), avant, versDto(p));
        return versDto(p);
    }

    // ------------------------------------------------------------------- stock

    @Transactional(readOnly = true)
    public List<StockProduit> stock(Long agenceId) {
        Agence agence = perimetre.exiger(agenceId);
        List<LotCarte> tous = lots.findByAgenceIdInOrderByDateEntreeDescIdDesc(List.of(agence.getId()));
        Map<Long, String> nomsComptes = comptes.findByAgenceIdOrderByIdAsc(agence.getId()).stream()
                .collect(Collectors.toMap(CompteAgence::getId, CompteAgence::getLibelle));
        List<StockProduit> resultat = new ArrayList<>();
        for (ProduitCarte p : produits.findAllByOrderByNomAsc()) {
            List<LotCarte> lotsProduit = tous.stream().filter(l -> l.getProduitId().equals(p.getId())).toList();
            int disponible = lotsProduit.stream().mapToInt(LotCarte::getQuantiteRestante).sum();
            long valeur = lotsProduit.stream().mapToLong(l -> l.getQuantiteRestante() * l.getCoutUnitaire()).sum();
            // Marge estimee sur le prochain lot a sortir (le plus ancien encore garni).
            long prochainCout = lotsProduit.stream().filter(l -> l.getQuantiteRestante() > 0)
                    .reduce((a, b) -> b).map(LotCarte::getCoutUnitaire).orElse(0L);
            resultat.add(new StockProduit(p.getId(), p.getNom(), p.getPrixVente(), disponible, valeur, p.getSeuilAlerte(),
                    p.isActif() && disponible <= p.getSeuilAlerte(), p.getPrixVente() - prochainCout,
                    lotsProduit.stream().map(l -> versDto(l, nomsComptes)).toList()));
        }
        return resultat;
    }

    /** Entree d'un lot : achat paye depuis un compte, ou stock initial sans paiement. */
    public List<StockProduit> entrer(Entree e, UtilisateurPrincipal auteur) {
        Agence agence = perimetre.exiger(e.agenceId());
        ProduitCarte p = produit(e.produitId());
        CompteAgence compte = null;
        if (e.compteId() != null) {
            compte = comptes.findById(e.compteId()).orElseThrow(() -> ResourceNotFoundException.of("Compte", e.compteId()));
            if (!compte.getAgenceId().equals(agence.getId()) || !compte.isActif()) {
                throw new BusinessException("Le compte " + compte.getLibelle() + " ne peut pas payer ce lot");
            }
        }
        LotCarte l = new LotCarte();
        l.setAgenceId(agence.getId());
        l.setProduitId(p.getId());
        l.setNumero(numerotation.prochainNumero("LOT"));
        l.setDateEntree(Instant.now());
        l.setQuantiteInitiale(e.quantite());
        l.setQuantiteRestante(e.quantite());
        l.setCoutUnitaire(e.coutUnitaire());
        l.setFournisseur(texte(e.fournisseur()));
        l.setReference(texte(e.reference()));
        l.setCompteId(compte == null ? null : compte.getId());
        l.setAuteurNom(auteur.getNomComplet());
        l = lots.save(l);
        long total = (long) e.quantite() * e.coutUnitaire();
        if (compte != null && total > 0) {
            Ecriture ecriture = registre.enregistrer(new RegistreService.Entete(agence.getId(), NatureEcriture.CARTE,
                    "Achat " + l.getNumero() + " : " + e.quantite() + " " + p.getNom() + " a " + RegistreService.francs(e.coutUnitaire()),
                    null, "LOT_CARTE", l.getId()), List.of(RegistreService.Mouvement.de(compte.getId(), -total)));
            l.setEcritureId(ecriture.getId());
        }
        audit.tracer(agence.getId(), compte == null ? "STOCK_INITIAL" : "ACHAT", "LOT_CARTE", l.getId(), e.quantite() + " " + p.getNom()
                + " entres en stock (" + l.getNumero() + ") a " + RegistreService.francs(e.coutUnitaire()) + " piece", null, e);
        return stock(agence.getId());
    }

    // ------------------------------------------------------------------ ventes

    /**
     * Vend des cartes : le stock est pris aux lots les plus anciens (verrouilles,
     * deux ventes simultanees ne vendent pas la meme carte), le prix entre en caisse.
     */
    public VenteDto vendre(Vente v, UtilisateurPrincipal caissier) {
        Agence agence = perimetre.exiger(v.agenceId());
        if (!agence.isActif()) {
            throw new BusinessException("L'agence " + agence.getNom() + " est fermee");
        }
        ProduitCarte p = produit(v.produitId());
        if (!p.isActif()) {
            throw new BusinessException("La carte " + p.getNom() + " n'est plus vendue");
        }
        // Caisse du cash : celle du caissier, ou la caisse generale pour un superviseur sans caisse.
        Long sessionId = null;
        CompteAgence caisse;
        var session = sessions.ouverte(agence.getId(), caissier.getId());
        if (session.isPresent()) {
            sessionId = session.get().getId();
            caisse = comptes.findById(session.get().getCompteId()).orElseThrow();
        } else if (caissierSeul()) {
            throw new BusinessException("Votre caisse n'est pas ouverte dans l'agence " + agence.getNom()
                    + " : demandez a votre superviseur de l'ouvrir");
        } else {
            caisse = compteService.synchroniser(agence).stream().filter(c -> c.getType() == TypeCompte.CAISSE_GENERALE).findFirst()
                    .orElseThrow(() -> new BusinessException("L'agence n'a pas de caisse generale"));
        }

        List<LotCarte> disponibles = lots.disponiblesVerrouilles(agence.getId(), p.getId());
        for (LotCarte l : disponibles) {
            em.refresh(l);
        }
        int stock = disponibles.stream().mapToInt(LotCarte::getQuantiteRestante).sum();
        if (stock < v.quantite()) {
            throw new BusinessException("Stock insuffisant de " + p.getNom() + " : " + stock + " carte(s) disponible(s) pour "
                    + v.quantite() + " demandee(s)");
        }
        VenteCarte vente = new VenteCarte();
        int reste = v.quantite();
        long cout = 0;
        for (LotCarte l : disponibles) {
            if (reste == 0) {
                break;
            }
            int pris = Math.min(reste, l.getQuantiteRestante());
            l.setQuantiteRestante(l.getQuantiteRestante() - pris);
            vente.getLots().add(new ConsommationLot(l.getId(), pris, l.getCoutUnitaire()));
            cout += (long) pris * l.getCoutUnitaire();
            reste -= pris;
        }
        Instant maintenant = Instant.now();
        vente.setAgenceId(agence.getId());
        vente.setProduitId(p.getId());
        vente.setReference(numerotation.prochainNumero("VCA"));
        vente.setDateVente(maintenant);
        vente.setJournee(registre.journee(maintenant));
        vente.setQuantite(v.quantite());
        vente.setPrixUnitaire(p.getPrixVente());
        vente.setMontant((long) v.quantite() * p.getPrixVente());
        vente.setCoutTotal(cout);
        vente.setMarge(vente.getMontant() - cout);
        vente.setCompteCashId(caisse.getId());
        vente.setSessionId(sessionId);
        vente.setClientNom(texte(v.clientNom()));
        vente.setClientTelephone(texte(v.clientTelephone()));
        vente.setCaissierId(caissier.getId());
        vente.setCaissierNom(caissier.getNomComplet());
        vente = ventes.save(vente);
        if (vente.getMontant() > 0) {
            Ecriture e = registre.enregistrer(new RegistreService.Entete(agence.getId(), NatureEcriture.CARTE,
                    vente.getReference() + " - Vente de " + v.quantite() + " " + p.getNom(), null, "VENTE_CARTE", vente.getId()),
                    List.of(RegistreService.Mouvement.de(caisse.getId(), vente.getMontant())));
            vente.setEcritureId(e.getId());
        }
        return versDto(vente, contexte());
    }

    /** Annule une vente : l'argent sort de la caisse et les cartes reviennent dans leurs lots. */
    public VenteDto annuler(Long id, String motif, UtilisateurPrincipal auteur) {
        VenteCarte v = ventes.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Vente de cartes", id));
        perimetre.exiger(v.getAgenceId());
        if (v.getStatut() != VenteCarte.Statut.VALIDEE) {
            throw new BusinessException("La vente " + v.getReference() + " est deja annulee");
        }
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif de l'annulation est obligatoire");
        }
        verrou.exigerOuverte(v.getAgenceId(), v.getJournee(), "l'annulation de " + v.getReference());
        if (v.getEcritureId() != null) {
            v.setAnnulationEcritureId(registre.contrePasser(v.getEcritureId(), motif.trim()).getId());
        }
        for (ConsommationLot c : v.getLots()) {
            LotCarte l = lots.findById(c.getLotId()).orElseThrow();
            em.refresh(l, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            l.setQuantiteRestante(l.getQuantiteRestante() + c.getQuantite());
        }
        v.setStatut(VenteCarte.Statut.ANNULEE);
        v.setAnnuleeLe(Instant.now());
        v.setAnnuleeParNom(auteur.getNomComplet());
        v.setMotifAnnulation(motif.trim());
        audit.tracer(v.getAgenceId(), "ANNULATION", "VENTE_CARTE", v.getId(), "Vente " + v.getReference() + " annulee (" + motif.trim()
                + "), " + v.getQuantite() + " carte(s) remise(s) en stock", "VALIDEE", "ANNULEE");
        return versDto(v, contexte());
    }

    @Transactional(readOnly = true)
    public Page<VenteDto> lister(Long agenceId, Pageable pageable) {
        Set<Long> agences = agences(agenceId);
        Contexte ctx = contexte();
        return ventes.findByAgenceIdInOrderByDateVenteDescIdDesc(agences.isEmpty() ? Set.of(-1L) : agences, pageable)
                .map(v -> versDto(v, ctx));
    }

    // ------------------------------------------------------------------- suivi

    @Transactional(readOnly = true)
    public TotauxVentes totaux(Set<Long> agences, LocalDate du, LocalDate au) {
        if (agences.isEmpty()) {
            return new TotauxVentes(0, 0, 0, 0, 0);
        }
        Object[] r = ventes.totaux(agences, du, au).get(0);
        return new TotauxVentes(nombre(r[0]), nombre(r[1]), nombre(r[2]), nombre(r[3]), nombre(r[4]));
    }

    /** Ventes de la periode et suivi des comptes de recharge (solde, approvisionnements, recharges, commissions). */
    @Transactional(readOnly = true)
    public Suivi suivi(Long agenceId, LocalDate du, LocalDate au) {
        Set<Long> agences = agences(agenceId);
        TotauxVentes totaux = totaux(agences, du, au);
        if (agences.isEmpty()) {
            return new Suivi(totaux, new SuiviRecharge(0, 0, 0, 0, 0, 0));
        }
        List<Long> recharges = comptes.findByAgenceIdIn(agences).stream().filter(c -> c.getType() == TypeCompte.RECHARGE)
                .map(CompteAgence::getId).toList();
        long soldeActuel = comptes.findAllById(recharges).stream().mapToLong(CompteAgence::getSolde).sum();
        long ouverture = 0;
        long approvisionnements = 0;
        if (!recharges.isEmpty()) {
            Object[] r = em.createQuery("""
                            select coalesce(sum(case when e.nature = :ouverture then l.montant else 0 end), 0),
                                   coalesce(sum(case when e.nature <> :ouverture and e.nature <> :transaction and e.nature <> :annulation
                                                     and l.montant > 0 and l.journee >= :du and l.journee <= :au then l.montant else 0 end), 0)
                            from LigneRegistre l join l.ecriture e where l.compteId in :comptes
                            """, Object[].class)
                    .setParameter("ouverture", NatureEcriture.OUVERTURE)
                    .setParameter("transaction", NatureEcriture.TRANSACTION)
                    .setParameter("annulation", NatureEcriture.ANNULATION)
                    .setParameter("du", du).setParameter("au", au)
                    .setParameter("comptes", recharges)
                    .getSingleResult();
            ouverture = nombre(r[0]);
            approvisionnements = nombre(r[1]);
        }
        Object[] t = em.createQuery("""
                        select count(t), coalesce(sum(t.montant), 0), coalesce(sum(t.commission), 0) from TransactionAgence t
                        where t.agenceId in :agences and t.compteVirtuel = :recharge and t.statut = :validee
                          and t.journee >= :du and t.journee <= :au
                        """, Object[].class)
                .setParameter("agences", agences)
                .setParameter("recharge", tg.parcauto.api.agence.catalogue.TypeOperation.CompteVirtuel.RECHARGE)
                .setParameter("validee", TransactionAgence.Statut.VALIDEE)
                .setParameter("du", du).setParameter("au", au)
                .getSingleResult();
        return new Suivi(totaux, new SuiviRecharge(ouverture, soldeActuel, approvisionnements, nombre(t[0]), nombre(t[1]), nombre(t[2])));
    }

    /** Cartes dont le stock est au seuil d'alerte ou en dessous, par agence. */
    @Transactional(readOnly = true)
    public List<String> alertesStock(Set<Long> agences) {
        List<String> alertes = new ArrayList<>();
        Map<Long, String> noms = perimetre.visibles().stream().collect(Collectors.toMap(Agence::getId, Agence::getNom));
        List<LotCarte> tous = agences.isEmpty() ? List.of() : lots.findByAgenceIdInOrderByDateEntreeDescIdDesc(agences);
        for (ProduitCarte p : produits.findAllByOrderByNomAsc()) {
            if (!p.isActif()) {
                continue;
            }
            for (Long agence : agences) {
                int dispo = tous.stream().filter(l -> l.getAgenceId().equals(agence) && l.getProduitId().equals(p.getId()))
                        .mapToInt(LotCarte::getQuantiteRestante).sum();
                boolean dejaVendue = tous.stream().anyMatch(l -> l.getAgenceId().equals(agence) && l.getProduitId().equals(p.getId()));
                if (dejaVendue && dispo <= p.getSeuilAlerte()) {
                    alertes.add(p.getNom() + " a " + noms.getOrDefault(agence, "?") + " : " + dispo + " en stock (seuil " + p.getSeuilAlerte() + ")");
                }
            }
        }
        return alertes;
    }

    // ------------------------------------------------------------------ outils

    private ProduitCarte produit(Long id) {
        return produits.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Carte", id));
    }

    private Set<Long> agences(Long agenceId) {
        return agenceId != null ? Set.of(perimetre.exiger(agenceId).getId())
                : perimetre.visibles().stream().map(Agence::getId).collect(Collectors.toSet());
    }

    private static boolean caissierSeul() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return true;
        }
        Set<String> roles = auth.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toSet());
        return !roles.contains("ROLE_ADMIN") && !roles.contains("ROLE_SUPERVISEUR_AGENCE");
    }

    private static long nombre(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    private static String texte(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ProduitDto versDto(ProduitCarte p) {
        return new ProduitDto(p.getId(), p.getNom(), p.getPrixVente(), p.getSeuilAlerte(), p.isActif());
    }

    private static LotDto versDto(LotCarte l, Map<Long, String> comptes) {
        return new LotDto(l.getId(), l.getNumero(), l.getAgenceId(), l.getProduitId(), l.getDateEntree(), l.getQuantiteInitiale(),
                l.getQuantiteRestante(), l.getCoutUnitaire(), l.getFournisseur(), l.getReference(),
                l.getCompteId() == null ? "Stock initial" : comptes.get(l.getCompteId()), l.getAuteurNom());
    }

    private record Contexte(Map<Long, String> agences, Map<Long, String> produits, Map<Long, String> comptes, boolean annulation) {
    }

    private Contexte contexte() {
        List<Agence> visibles = perimetre.visibles();
        return new Contexte(visibles.stream().collect(Collectors.toMap(Agence::getId, Agence::getNom)),
                produits.findAll().stream().collect(Collectors.toMap(ProduitCarte::getId, ProduitCarte::getNom)),
                comptes.findByAgenceIdIn(visibles.stream().map(Agence::getId).toList()).stream()
                        .collect(Collectors.toMap(CompteAgence::getId, CompteAgence::getLibelle)),
                droits.courants().contains(PermissionAgence.TRANSACTION_ANNULER));
    }

    private static VenteDto versDto(VenteCarte v, Contexte ctx) {
        return new VenteDto(v.getId(), v.getReference(), v.getAgenceId(), ctx.agences().get(v.getAgenceId()), v.getProduitId(),
                ctx.produits().get(v.getProduitId()), v.getDateVente(), v.getJournee(), v.getQuantite(), v.getPrixUnitaire(),
                v.getMontant(), v.getCoutTotal(), v.getMarge(), ctx.comptes().get(v.getCompteCashId()), v.getClientNom(),
                v.getClientTelephone(), v.getCaissierNom(), v.getStatut(), v.getAnnuleeLe(), v.getAnnuleeParNom(),
                v.getMotifAnnulation(), ctx.annulation() && v.getStatut() == VenteCarte.Statut.VALIDEE);
    }
}
