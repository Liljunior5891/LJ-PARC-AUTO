package tg.parcauto.api.agence.operation;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.agence.Agence;
import tg.parcauto.api.agence.AuditAgenceService;
import tg.parcauto.api.agence.DroitsAgence;
import tg.parcauto.api.agence.PerimetreAgence;
import tg.parcauto.api.agence.PermissionAgence;
import tg.parcauto.api.agence.ReglageAgence;
import tg.parcauto.api.agence.ReglageAgenceRepository;
import tg.parcauto.api.agence.catalogue.Reseau;
import tg.parcauto.api.agence.catalogue.ReseauRepository;
import tg.parcauto.api.agence.catalogue.ServiceFinancier;
import tg.parcauto.api.agence.catalogue.ServiceFinancierRepository;
import tg.parcauto.api.agence.catalogue.TypeOperation;
import tg.parcauto.api.agence.catalogue.TypeOperationRepository;
import tg.parcauto.api.agence.commission.Bareme;
import tg.parcauto.api.agence.commission.BaremeService;
import tg.parcauto.api.agence.tresorerie.CompteAgence;
import tg.parcauto.api.agence.tresorerie.CompteService;
import tg.parcauto.api.agence.tresorerie.Ecriture;
import tg.parcauto.api.agence.tresorerie.JourneeCaisse;
import tg.parcauto.api.agence.tresorerie.NatureEcriture;
import tg.parcauto.api.agence.tresorerie.RegistreService;
import tg.parcauto.api.agence.tresorerie.TypeCompte;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.common.SpecificationUtils;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Transactions des agences : apercu avant validation, enregistrement,
 * annulation et historique.
 * <p>
 * Une transaction est validee des sa saisie : la regle de mouvement de son
 * type d'operation fait bouger le cash et le virtuel du service, et la
 * commission du bareme est creditee, le tout en une ecriture du registre.
 * Un solde insuffisant bloque l'operation avec le compte et le montant manquant.
 * La commission proposee ne se change qu'avec le droit voulu, et la
 * modification reste tracee. Une operation identique toute recente (meme
 * service, operation, montant et client) demande une confirmation.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TransactionService {

    private static final DateTimeFormatter AAAAMMJJ = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(JourneeCaisse.FUSEAU);
    /** Origine des ecritures du registre ecrites par une transaction. */
    public static final String ORIGINE = "TRANSACTION";

    private final TransactionAgenceRepository repository;
    private final ClientAgenceRepository clients;
    private final PerimetreAgence perimetre;
    private final DroitsAgence droits;
    private final ReglageAgenceRepository reglages;
    private final ServiceFinancierRepository services;
    private final TypeOperationRepository types;
    private final ReseauRepository reseaux;
    private final BaremeService baremes;
    private final CompteService comptes;
    private final RegistreService registre;
    private final AuditAgenceService audit;
    private final tg.parcauto.api.agence.caisse.SessionCaisseService sessions;
    private final tg.parcauto.api.agence.cloture.VerrouJournee verrou;

    @PersistenceContext
    private EntityManager em;

    public record Saisie(
            @NotNull Long agenceId,
            @NotNull Long serviceId,
            @NotNull Long typeOperationId,
            Long reseauId,
            @NotNull @Positive Long montant,
            /** Nulle : la commission du bareme. */
            @PositiveOrZero Long commission,
            @Size(max = 255) String motifCommission,
            @Size(max = 80) String referenceOperateur,
            @Size(max = 30) String clientTelephone,
            @Size(max = 150) String clientNom,
            @Size(max = 40) String clientTypePiece,
            @Size(max = 60) String clientNumeroPiece,
            /** Numero de la carte rechargee ; seuls les 4 derniers chiffres sont gardes. */
            @Size(max = 30) String carte,
            @Size(max = 500) String observations,
            boolean confirmerDoublon) {
    }

    /** Effet de l'operation sur un compte. */
    public record Effet(Long compteId, String compte, String role, long soldeActuel, long variation, long soldeApres,
                        boolean insuffisant, long manque) {
    }

    public record Doublon(Long id, String reference, Instant date, String caissierNom, String texte) {
    }

    /** Resume montre avant validation. */
    public record Apercu(long commissionProposee, long commission, String baremeLibelle, String detailCommission,
                         boolean baremeExemple, boolean commissionModifiable,
                         /** Commission versee plus tard par l'operateur : aucun compte n'est credite maintenant. */
                         boolean commissionDifferee, List<Effet> effets, Doublon doublon, List<String> blocages) {
    }

    public record TransactionDto(
            Long id, String reference, Instant dateOperation, LocalDate journee, Long agenceId, String agenceNom,
            Long serviceId, String serviceNom, String categorie, Long reseauId, String reseauNom, Long typeOperationId,
            String typeLibelle, long montant, int effetCash, int effetVirtuel, TypeOperation.CompteVirtuel compteVirtuel,
            long commission, long commissionProposee, boolean commissionModifiee, String motifCommission,
            Bareme.CompteCredit compteCommission, String referenceOperateur, String clientNom, String clientTelephone,
            String clientTypePiece, String clientNumeroPiece, String carteMasquee, String observations, Long caissierId,
            String caissierNom, TransactionAgence.Statut statut, TransactionAgence.StatutCommission commissionStatut,
            boolean doublonConfirme, Instant annuleeLe,
            String annuleeParNom, String motifAnnulation, boolean annulable) {
    }

    public record Filtres(Long agenceId, LocalDate du, LocalDate au, Long serviceId, Long reseauId, Long typeOperationId,
                          Long caissierId, TransactionAgence.Statut statut, Long montantMin, Long montantMax, String q) {
    }

    public record Resume(long nombre, long volume, long commissions, long annulees) {
    }

    public record ClientDto(Long id, String telephone, String nom, String typePiece, String numeroPiece) {
    }

    // ---------------------------------------------------------------- saisie

    /** Ce que ferait la transaction, sans rien enregistrer (seuls des comptes manquants peuvent etre crees). */
    public Apercu apercu(Saisie s) {
        Plan plan = preparer(s);
        List<String> blocages = new ArrayList<>(plan.blocages);
        List<Effet> effets = effets(plan);
        effets.stream().filter(Effet::insuffisant)
                .forEach(e -> blocages.add(e.compte() + " insuffisant : il manque " + RegistreService.francs(e.manque())));
        return new Apercu(plan.commission.commission(), plan.commissionRetenue, plan.commission.baremeLibelle(),
                plan.commission.detail(), plan.commission.exemple(), droits.courants().contains(PermissionAgence.COMMISSION_MODIFIER),
                plan.service.isCommissionDifferee(), effets, plan.doublon, blocages);
    }

    /** Valide la transaction : ecriture au registre, fiche client, trace d'une commission modifiee. */
    public TransactionDto enregistrer(Saisie s, UtilisateurPrincipal caissier) {
        Plan plan = preparer(s);
        if (!plan.blocages.isEmpty()) {
            throw new BusinessException(plan.blocages.get(0));
        }
        if (plan.doublon != null && !s.confirmerDoublon()) {
            throw new BusinessException("Doublon possible : " + plan.doublon.texte() + ". Confirmez pour enregistrer quand meme.");
        }
        boolean modifiee = plan.commissionRetenue != plan.commission.commission();
        if (modifiee && (s.motifCommission() == null || s.motifCommission().isBlank())) {
            throw new BusinessException("Indiquez pourquoi la commission proposee (" + RegistreService.francs(plan.commission.commission())
                    + ") est modifiee");
        }

        Instant maintenant = Instant.now();
        TransactionAgence t = new TransactionAgence();
        t.setAgenceId(plan.agence.getId());
        t.setDateOperation(maintenant);
        t.setJournee(registre.journee(maintenant));
        t.setReference(prochaineReference(t.getJournee()));
        t.setServiceId(plan.service.getId());
        t.setReseauId(plan.reseau == null ? null : plan.reseau.getId());
        t.setTypeOperationId(plan.type.getId());
        t.setMontant(s.montant());
        t.setEffetCash(plan.type.getEffetCash());
        t.setEffetVirtuel(plan.type.getEffetVirtuel());
        t.setCompteVirtuel(plan.type.getCompteVirtuel());
        t.setCommission(plan.commissionRetenue);
        t.setCommissionProposee(plan.commission.commission());
        t.setBaremeId(plan.commission.baremeId());
        t.setCompteCommission(plan.commission.compteCredit());
        t.setMotifCommission(modifiee ? s.motifCommission().trim() : null);
        // Versement differe : la commission est acquise mais attend le versement de l'operateur.
        t.setCommissionStatut(plan.service.isCommissionDifferee() && plan.commissionRetenue > 0
                ? TransactionAgence.StatutCommission.ACQUISE : TransactionAgence.StatutCommission.CREDITEE);
        t.setReferenceOperateur(texte(s.referenceOperateur()));
        t.setObservations(texte(s.observations()));
        t.setCarteMasquee(carteMasquee(s.carte()));
        t.setCompteCashId(plan.caisse.getId());
        t.setSessionId(plan.sessionId);
        t.setCaissierId(caissier.getId());
        t.setCaissierNom(caissier.getNomComplet());
        t.setDoublonConfirme(plan.doublon != null);
        ClientAgence client = client(s);
        if (client != null) {
            t.setClientId(client.getId());
        }
        t.setClientTelephone(texte(s.clientTelephone()));
        t.setClientNom(texte(s.clientNom()));
        t.setClientTypePiece(texte(s.clientTypePiece()));
        t.setClientNumeroPiece(texte(s.clientNumeroPiece()));
        t = repository.save(t);

        Ecriture ecriture = registre.enregistrer(new RegistreService.Entete(plan.agence.getId(), NatureEcriture.TRANSACTION,
                libelle(plan, t), null, ORIGINE, t.getId()), plan.mouvements);
        t.setEcritureId(ecriture.getId());

        if (modifiee) {
            audit.tracer(plan.agence.getId(), "COMMISSION_MODIFIEE", "TRANSACTION", t.getId(),
                    "Commission de " + t.getReference() + " modifiee : " + RegistreService.francs(t.getCommissionProposee())
                            + " proposes, " + RegistreService.francs(t.getCommission()) + " retenus (" + t.getMotifCommission() + ")",
                    t.getCommissionProposee(), t.getCommission());
        }
        return versDto(t, Contexte.charger(this));
    }

    /** Annule une transaction validee par l'ecriture inverse ; le motif est obligatoire et trace. */
    public TransactionDto annuler(Long id, String motif, UtilisateurPrincipal auteur) {
        TransactionAgence t = charger(id);
        perimetre.exiger(t.getAgenceId());
        if (t.getStatut() != TransactionAgence.Statut.VALIDEE) {
            throw new BusinessException("La transaction " + t.getReference() + " est deja annulee");
        }
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif de l'annulation est obligatoire");
        }
        verrou.exigerOuverte(t.getAgenceId(), t.getJournee(), "l'annulation de " + t.getReference());
        sessions.exigerSessionOuverte(t.getSessionId(), "l'annulation de " + t.getReference());
        if (t.getVersementId() != null) {
            throw new BusinessException("La commission de " + t.getReference() + " est deja rapprochee d'un versement de l'operateur :"
                    + " annulez d'abord ce versement");
        }
        Ecriture inverse = registre.contrePasser(t.getEcritureId(), motif.trim());
        t.setStatut(TransactionAgence.Statut.ANNULEE);
        t.setAnnulationEcritureId(inverse.getId());
        t.setAnnuleeLe(Instant.now());
        t.setAnnuleeParId(auteur.getId());
        t.setAnnuleeParNom(auteur.getNomComplet());
        t.setMotifAnnulation(motif.trim());
        audit.tracer(t.getAgenceId(), "ANNULATION", "TRANSACTION", t.getId(), "Transaction " + t.getReference() + " annulee ("
                + motif.trim() + ")", "VALIDEE", "ANNULEE");
        return versDto(t, Contexte.charger(this));
    }

    // ------------------------------------------------------------- historique

    @Transactional(readOnly = true)
    public Page<TransactionDto> lister(Filtres f, Pageable pageable) {
        Specification<TransactionAgence> spec = (root, query, cb) -> filtrer(root, cb, f);
        Pageable trie = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "dateOperation").and(Sort.by(Sort.Direction.DESC, "id")));
        Contexte ctx = Contexte.charger(this);
        return repository.findAll(spec, trie).map(t -> versDto(t, ctx));
    }

    /** Nombre, volume et commissions des transactions validees qui repondent aux filtres. */
    @Transactional(readOnly = true)
    public Resume resume(Filtres f) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<TransactionAgence> root = cq.from(TransactionAgence.class);
        Predicate filtre = filtrer(root, cb, f);
        cq.multiselect(
                cb.sum(cb.<Long>selectCase().when(cb.equal(root.get("statut"), TransactionAgence.Statut.VALIDEE), 1L).otherwise(0L)),
                cb.sum(cb.<Long>selectCase().when(cb.equal(root.get("statut"), TransactionAgence.Statut.VALIDEE), root.<Long>get("montant")).otherwise(0L)),
                cb.sum(cb.<Long>selectCase().when(cb.equal(root.get("statut"), TransactionAgence.Statut.VALIDEE), root.<Long>get("commission")).otherwise(0L)),
                cb.sum(cb.<Long>selectCase().when(cb.equal(root.get("statut"), TransactionAgence.Statut.ANNULEE), 1L).otherwise(0L)))
                .where(filtre);
        Object[] r = em.createQuery(cq).getSingleResult();
        return new Resume(nombre(r[0]), nombre(r[1]), nombre(r[2]), nombre(r[3]));
    }

    @Transactional(readOnly = true)
    public TransactionDto detail(Long id) {
        TransactionAgence t = charger(id);
        perimetre.exiger(t.getAgenceId());
        if (!droits.courants().contains(PermissionAgence.HISTORIQUE_COMPLET) && !t.getCaissierId().equals(moi())) {
            throw new org.springframework.security.access.AccessDeniedException("Cette transaction a ete saisie par un autre caissier");
        }
        return versDto(t, Contexte.charger(this));
    }

    /** Fiches clients dont le telephone ou le nom contient le texte. */
    @Transactional(readOnly = true)
    public List<ClientDto> clients(String q) {
        if (!SpecificationUtils.renseigne(q) || q.trim().length() < 2) {
            return List.of();
        }
        return clients.rechercher(SpecificationUtils.motif(q), PageRequest.of(0, 8)).stream()
                .map(c -> new ClientDto(c.getId(), c.getTelephone(), c.getNom(), c.getTypePiece(), c.getNumeroPiece()))
                .toList();
    }

    // ----------------------------------------------------------- preparation

    /** Tout ce que la transaction ferait, controle compris. */
    private static final class Plan {
        Agence agence;
        /** Caisse du cash et session du caissier (nulle : caisse generale). */
        CompteAgence caisse;
        Long sessionId;
        ServiceFinancier service;
        TypeOperation type;
        Reseau reseau;
        BaremeService.Commission commission;
        long commissionRetenue;
        final Map<Long, CompteAgence> comptes = new LinkedHashMap<>();
        final Map<Long, String> roles = new LinkedHashMap<>();
        final List<RegistreService.Mouvement> mouvements = new ArrayList<>();
        final List<String> blocages = new ArrayList<>();
        Doublon doublon;
    }

    private Plan preparer(Saisie s) {
        Plan p = new Plan();
        p.agence = perimetre.exiger(s.agenceId());
        if (!p.agence.isActif()) {
            throw new BusinessException("L'agence " + p.agence.getNom() + " est fermee");
        }
        p.service = services.findById(s.serviceId()).orElseThrow(() -> ResourceNotFoundException.of("Service", s.serviceId()));
        if (!p.service.isActif()) {
            throw new BusinessException("Le service " + p.service.getNom() + " est desactive");
        }
        p.type = types.findById(s.typeOperationId())
                .orElseThrow(() -> ResourceNotFoundException.of("Type d'operation", s.typeOperationId()));
        if (!p.type.getService().getId().equals(p.service.getId())) {
            throw new BusinessException("L'operation " + p.type.getLibelle() + " n'appartient pas au service " + p.service.getNom());
        }
        if (!p.type.isActif()) {
            throw new BusinessException("L'operation " + p.type.getLibelle() + " est desactivee");
        }
        if (p.type.getNature() == TypeOperation.Nature.VENTE_CARTE) {
            throw new BusinessException("La vente de cartes se fait depuis l'ecran des cartes prepayees (gestion du stock)");
        }
        List<Reseau> reseauxActifs = reseaux.findAllByOrderByOrdreAscNomAsc().stream()
                .filter(r -> r.getService().getId().equals(p.service.getId()) && r.isActif()).toList();
        if (s.reseauId() != null) {
            p.reseau = reseauxActifs.stream().filter(r -> r.getId().equals(s.reseauId())).findFirst()
                    .orElseThrow(() -> new BusinessException("Ce reseau n'est pas propose pour " + p.service.getNom()));
        } else if (!reseauxActifs.isEmpty()) {
            throw new BusinessException("Choisissez le reseau (" + reseauxActifs.stream().map(Reseau::getNom)
                    .collect(Collectors.joining(", ")) + ")");
        }
        if (p.type.getReseau() != null && (p.reseau == null || !p.type.getReseau().getId().equals(p.reseau.getId()))) {
            throw new BusinessException("L'operation " + p.type.getLibelle() + " est propre au reseau " + p.type.getReseau().getNom());
        }
        long montant = s.montant();
        ReglageAgence reglage = reglages.findFirstByOrderByIdAsc().orElseGet(ReglageAgence::new);
        if (montant > reglage.getMontantMaxTransaction()) {
            p.blocages.add("Le montant depasse le maximum autorise par transaction ("
                    + RegistreService.francs(reglage.getMontantMaxTransaction()) + ")");
        }

        // Commission : celle du bareme, sauf modification autorisee.
        p.commission = baremes.commission(p.service.getId(), p.type.getId(), p.reseau == null ? null : p.reseau.getId(),
                montant, registre.journee(Instant.now()));
        p.commissionRetenue = p.commission.commission();
        if (s.commission() != null && s.commission() != p.commission.commission()) {
            droits.exiger(PermissionAgence.COMMISSION_MODIFIER);
            p.commissionRetenue = s.commission();
        }

        // Comptes touches. Le cash passe par la caisse ouverte du caissier ; sans caisse
        // ouverte, seuls le superviseur et l'administrateur travaillent sur la caisse generale.
        List<CompteAgence> liste = comptes.synchroniser(p.agence);
        Long moi = moi();
        var session = sessions.ouverte(p.agence.getId(), moi);
        if (session.isPresent()) {
            p.sessionId = session.get().getId();
            p.caisse = liste.stream().filter(c -> c.getId().equals(session.get().getCompteId())).findFirst()
                    .orElseThrow(() -> new BusinessException("La caisse du caissier est introuvable"));
        } else if (caissierSeul()) {
            throw new BusinessException(sessions.motifSansCaisse(p.agence.getId(), p.agence.getNom(), moi));
        } else {
            p.caisse = trouver(liste, TypeCompte.CAISSE_GENERALE, null, "la caisse generale");
        }
        if (p.type.getEffetCash() != 0) {
            ajouter(p, p.caisse, "Cash", montant * p.type.getEffetCash());
        }
        CompteAgence virtuel = null;
        if (p.type.getCompteVirtuel() != TypeOperation.CompteVirtuel.AUCUN) {
            TypeCompte typeVirtuel = p.type.getCompteVirtuel() == TypeOperation.CompteVirtuel.FLOAT ? TypeCompte.FLOAT : TypeCompte.RECHARGE;
            virtuel = trouver(liste, typeVirtuel, p.service.getId(),
                    (typeVirtuel == TypeCompte.FLOAT ? "le float de " : "le compte de recharge de ") + p.service.getNom());
            ajouter(p, virtuel, typeVirtuel == TypeCompte.FLOAT ? "Virtuel" : "Recharge", montant * p.type.getEffetVirtuel());
        }
        if (p.commissionRetenue > 0 && !p.service.isCommissionDifferee()) {
            CompteAgence compteCommission = p.commission.compteCredit() == Bareme.CompteCredit.FLOAT
                    ? trouver(liste, TypeCompte.FLOAT, p.service.getId(), "le float de " + p.service.getNom())
                    : trouver(liste, TypeCompte.COMMISSIONS, null, "le compte des commissions");
            ajouter(p, compteCommission, "Commission", p.commissionRetenue);
        }

        // Doublon : meme operation, meme montant et meme client, dans le delai regle.
        if (reglage.getDelaiDoublonMinutes() > 0) {
            Instant depuis = Instant.now().minusSeconds(reglage.getDelaiDoublonMinutes() * 60L);
            String tel = texte(s.clientTelephone());
            repository.recentesIdentiques(p.agence.getId(), p.service.getId(), p.type.getId(), montant, depuis).stream()
                    .filter(t -> Objects.equals(t.getReseauId(), p.reseau == null ? null : p.reseau.getId()))
                    .filter(t -> Objects.equals(t.getClientTelephone(), tel))
                    .findFirst()
                    .ifPresent(t -> p.doublon = new Doublon(t.getId(), t.getReference(), t.getDateOperation(), t.getCaissierNom(),
                            "la transaction " + t.getReference() + " identique a ete saisie le " + HEURE.format(t.getDateOperation())
                                    + " par " + t.getCaissierNom()));
        }
        return p;
    }

    private static void ajouter(Plan p, CompteAgence compte, String role, long variation) {
        p.comptes.put(compte.getId(), compte);
        p.roles.merge(compte.getId(), role, (a, b) -> a + " + " + b);
        p.mouvements.add(new RegistreService.Mouvement(compte.getId(), variation, p.service.getId(),
                p.reseau == null ? null : p.reseau.getId()));
    }

    private static CompteAgence trouver(List<CompteAgence> liste, TypeCompte type, Long serviceId, String nom) {
        return liste.stream().filter(c -> c.getType() == type && Objects.equals(c.getServiceId(), serviceId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("L'agence n'a pas " + nom));
    }

    private static List<Effet> effets(Plan p) {
        Map<Long, Long> variations = new LinkedHashMap<>();
        p.mouvements.forEach(m -> variations.merge(m.compteId(), m.montant(), Long::sum));
        List<Effet> effets = new ArrayList<>();
        variations.forEach((id, variation) -> {
            CompteAgence c = p.comptes.get(id);
            long apres = c.getSolde() + variation;
            boolean insuffisant = variation < 0 && apres < -c.getDecouvertAutorise();
            effets.add(new Effet(id, c.getLibelle(), p.roles.get(id), c.getSolde(), variation, apres, insuffisant,
                    insuffisant ? -c.getDecouvertAutorise() - apres : 0));
        });
        return effets;
    }

    // ------------------------------------------------------------------ outils

    /** Reference unique du jour : TX-AAAAMMJJ-0001, attribuee par un compteur atomique. */
    private String prochaineReference(LocalDate journee) {
        String prefixe = "TX-" + journee.format(AAAAMMJJ);
        Number valeur = (Number) em.createNativeQuery("""
                        insert into compteur_numero (entreprise_id, prefixe, annee, valeur)
                        values (:entreprise, :prefixe, :annee, 1)
                        on conflict (entreprise_id, prefixe, annee)
                        do update set valeur = compteur_numero.valeur + 1
                        returning valeur
                        """)
                .setParameter("entreprise", ContexteEntreprise.exiger())
                .setParameter("prefixe", prefixe)
                .setParameter("annee", journee.getYear())
                .getSingleResult();
        return "%s-%04d".formatted(prefixe, valeur.longValue());
    }

    /** Cree ou complete la fiche du client a partir de son telephone. */
    private ClientAgence client(Saisie s) {
        String tel = texte(s.clientTelephone());
        if (tel == null) {
            return null;
        }
        ClientAgence c = clients.findByTelephone(tel).orElseGet(() -> {
            ClientAgence nouveau = new ClientAgence();
            nouveau.setTelephone(tel);
            return nouveau;
        });
        if (texte(s.clientNom()) != null) {
            c.setNom(texte(s.clientNom()));
        }
        if (texte(s.clientTypePiece()) != null) {
            c.setTypePiece(texte(s.clientTypePiece()));
        }
        if (texte(s.clientNumeroPiece()) != null) {
            c.setNumeroPiece(texte(s.clientNumeroPiece()));
        }
        return clients.save(c);
    }

    /** « **** 1234 » : seuls les 4 derniers chiffres de la carte sont gardes. */
    static String carteMasquee(String carte) {
        if (carte == null) {
            return null;
        }
        String chiffres = carte.replaceAll("\\D", "");
        if (chiffres.isEmpty()) {
            return null;
        }
        return "**** " + (chiffres.length() <= 4 ? chiffres : chiffres.substring(chiffres.length() - 4));
    }

    private static String libelle(Plan p, TransactionAgence t) {
        return t.getReference() + " - " + p.type.getLibelle() + " " + (p.reseau != null ? p.reseau.getNom() : p.service.getNom())
                + " " + RegistreService.francs(t.getMontant());
    }

    private Predicate filtrer(Root<TransactionAgence> root, CriteriaBuilder cb, Filtres f) {
        List<Predicate> p = new ArrayList<>();
        Set<Long> agences = f.agenceId() != null ? Set.of(perimetre.exiger(f.agenceId()).getId())
                : perimetre.visibles().stream().map(Agence::getId).collect(Collectors.toSet());
        p.add(agences.isEmpty() ? cb.disjunction() : root.get("agenceId").in(agences));
        if (!droits.courants().contains(PermissionAgence.HISTORIQUE_COMPLET)) {
            p.add(cb.equal(root.get("caissierId"), moi()));
        } else if (f.caissierId() != null) {
            p.add(cb.equal(root.get("caissierId"), f.caissierId()));
        }
        if (f.du() != null) {
            p.add(cb.greaterThanOrEqualTo(root.get("journee"), f.du()));
        }
        if (f.au() != null) {
            p.add(cb.lessThanOrEqualTo(root.get("journee"), f.au()));
        }
        if (f.serviceId() != null) {
            p.add(cb.equal(root.get("serviceId"), f.serviceId()));
        }
        if (f.reseauId() != null) {
            p.add(cb.equal(root.get("reseauId"), f.reseauId()));
        }
        if (f.typeOperationId() != null) {
            p.add(cb.equal(root.get("typeOperationId"), f.typeOperationId()));
        }
        if (f.statut() != null) {
            p.add(cb.equal(root.get("statut"), f.statut()));
        }
        if (f.montantMin() != null) {
            p.add(cb.greaterThanOrEqualTo(root.get("montant"), f.montantMin()));
        }
        if (f.montantMax() != null) {
            p.add(cb.lessThanOrEqualTo(root.get("montant"), f.montantMax()));
        }
        if (SpecificationUtils.renseigne(f.q())) {
            String motif = SpecificationUtils.motif(f.q());
            p.add(cb.or(cb.like(cb.lower(root.get("reference")), motif),
                    cb.like(cb.lower(cb.coalesce(root.get("referenceOperateur"), "")), motif),
                    cb.like(cb.coalesce(root.get("clientTelephone"), ""), motif),
                    cb.like(cb.lower(cb.coalesce(root.get("clientNom"), "")), motif)));
        }
        return cb.and(p.toArray(Predicate[]::new));
    }

    private static Long moi() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UtilisateurPrincipal u ? u.getId() : -1L;
    }

    /** Caissier d'agence sans profil de supervision : il ne travaille que sur sa caisse ouverte. */
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

    private TransactionAgence charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Transaction", id));
    }

    /** Noms du catalogue et des agences, charges une fois par reponse. */
    private record Contexte(Map<Long, String> agences, Map<Long, ServiceFinancier> services, Map<Long, String> reseaux,
                            Map<Long, String> types, boolean annulation) {

        static Contexte charger(TransactionService s) {
            return new Contexte(
                    s.perimetre.visibles().stream().collect(Collectors.toMap(Agence::getId, Agence::getNom)),
                    s.services.findAll().stream().collect(Collectors.toMap(ServiceFinancier::getId, Function.identity())),
                    s.reseaux.findAll().stream().collect(Collectors.toMap(Reseau::getId, Reseau::getNom)),
                    s.types.findAll().stream().collect(Collectors.toMap(TypeOperation::getId, TypeOperation::getLibelle)),
                    s.droits.courants().contains(PermissionAgence.TRANSACTION_ANNULER));
        }
    }

    private static TransactionDto versDto(TransactionAgence t, Contexte ctx) {
        ServiceFinancier s = ctx.services().get(t.getServiceId());
        return new TransactionDto(t.getId(), t.getReference(), t.getDateOperation(), t.getJournee(), t.getAgenceId(),
                ctx.agences().get(t.getAgenceId()), t.getServiceId(), s == null ? null : s.getNom(),
                s == null ? null : s.getCategorie(), t.getReseauId(), t.getReseauId() == null ? null : ctx.reseaux().get(t.getReseauId()),
                t.getTypeOperationId(), ctx.types().get(t.getTypeOperationId()), t.getMontant(), t.getEffetCash(),
                t.getEffetVirtuel(), t.getCompteVirtuel(), t.getCommission(), t.getCommissionProposee(),
                t.getCommission() != t.getCommissionProposee(), t.getMotifCommission(), t.getCompteCommission(),
                t.getReferenceOperateur(), t.getClientNom(), t.getClientTelephone(), t.getClientTypePiece(),
                t.getClientNumeroPiece(), t.getCarteMasquee(), t.getObservations(), t.getCaissierId(), t.getCaissierNom(),
                t.getStatut(), t.getCommissionStatut(), t.isDoublonConfirme(), t.getAnnuleeLe(), t.getAnnuleeParNom(), t.getMotifAnnulation(),
                ctx.annulation() && t.getStatut() == TransactionAgence.Statut.VALIDEE);
    }
}
