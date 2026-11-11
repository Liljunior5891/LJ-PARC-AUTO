package tg.parcauto.api.plateforme.abonnement;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.parc.document.StockageFichierService;
import tg.parcauto.api.plateforme.entreprise.Entreprise;
import tg.parcauto.api.plateforme.entreprise.EntrepriseRepository;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleService;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Abonnements SaaS des entreprises.
 * <p>
 * Une entreprise demarre par un essai gratuit sans limite. Elle choisit ensuite
 * une formule (un prix mensuel par module, des limites de volume) et ses
 * modules : elle paie la somme de leurs prix dans la formule. Elle
 * declare son paiement ; le super-administrateur le valide, ce qui ouvre la
 * periode, aligne les modules actifs sur ceux payes et numerote la facture.
 * <p>
 * A l'echeance, les jours de grace laissent tout fonctionner ; ensuite
 * l'entreprise passe en lecture seule jusqu'au paiement suivant.
 * <p>
 * Toutes les tables d'abonnement appartiennent a la plateforme : elles ne sont
 * pas cloisonnees et chaque requete nomme l'entreprise.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AbonnementService {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private final AbonnementRepository abonnements;
    private final FormuleRepository formules;
    private final tg.parcauto.api.common.audit.JournalActiviteService journal;
    private final PaiementAbonnementRepository paiements;
    private final ParametresAbonnementRepository parametresRepository;
    private final EntrepriseRepository entreprises;
    private final ModuleService moduleService;
    private final StockageFichierService stockage;
    private final JdbcTemplate jdbc;
    private final tg.parcauto.api.plateforme.abonnement.paygate.PayGateClient payGate;

    // ================================================================ vues

    public record FormuleVue(Long id, String nom, String description,
                             /** Prix mensuel de chaque module disponible (zero si non renseigne). */
                             Map<String, BigDecimal> prix,
                             Integer maxUtilisateurs, Integer maxVehicules, Integer maxAgences, Integer maxCaisses,
                             Integer maxEmployes, boolean active, boolean miseEnAvant, int ordre,
                             /** Entreprises abonnees a cette formule. */
                             long abonnes) {
    }

    public record FormuleRequete(@NotBlank @Size(max = 60) String nom,
                                 @Size(max = 300) String description,
                                 @NotNull Map<ModuleApplicatif, @NotNull @PositiveOrZero BigDecimal> prix,
                                 @Min(1) Integer maxUtilisateurs, @Min(0) Integer maxVehicules,
                                 @Min(0) Integer maxAgences, @Min(0) Integer maxCaisses, @Min(0) Integer maxEmployes,
                                 boolean active, boolean miseEnAvant, int ordre) {
    }

    /** Ligne de devis ou de facture : un module et son prix mensuel dans la formule. */
    public record Ligne(String module, String libelle, BigDecimal prixMensuel) {
    }

    public record ParametresVue(String devise, int dureeEssaiJours, int joursGrace, int joursRappel,
                                BigDecimal remiseTrimestrielle, BigDecimal remiseSemestrielle,
                                BigDecimal remiseAnnuelle, String instructionsPaiement, String mentionsFacture) {
    }

    public record ParametresRequete(@NotBlank @Size(max = 10) String devise,
                                    @Min(0) @Max(365) int dureeEssaiJours,
                                    @Min(0) @Max(90) int joursGrace,
                                    @Min(0) @Max(60) int joursRappel,
                                    @NotNull @PositiveOrZero @Max(90) BigDecimal remiseTrimestrielle,
                                    @NotNull @PositiveOrZero @Max(90) BigDecimal remiseSemestrielle,
                                    @NotNull @PositiveOrZero @Max(90) BigDecimal remiseAnnuelle,
                                    @Size(max = 2000) String instructionsPaiement,
                                    @Size(max = 2000) String mentionsFacture) {
    }

    /** Consommation d'une ressource face a la limite de la formule (nulle = illimitee). */
    public record Usage(String ressource, String libelle, long utilise, Integer limite) {
    }

    /** Etat resume, pour le bandeau de toutes les pages de l'entreprise. */
    public record EtatCourt(String etat, String etatLibelle, boolean essai, boolean lectureSeule, boolean rappel,
                            LocalDate fin, LocalDate finGrace, long joursRestants, String formuleNom) {
    }

    public record AbonnementVue(Long entrepriseId, String entrepriseNom, String etat, String etatLibelle,
                                boolean essai, boolean lectureSeule, boolean rappel,
                                LocalDate debut, LocalDate fin, LocalDate finGrace, long joursRestants,
                                Long formuleId, String formuleNom, String periodicite, String periodiciteLibelle,
                                List<String> modules, List<Usage> usages, boolean paiementEnAttente,
                                /** Somme mensuelle des modules payes, pour le revenu recurrent. */
                                BigDecimal mensualite) {
    }

    public record DemandeDevis(@NotNull Long formuleId, @NotNull Periodicite periodicite,
                               List<ModuleApplicatif> modules) {
    }

    public record Devis(Long formuleId, String formuleNom, String periodicite, int mois,
                        List<String> modules, List<Ligne> lignes, BigDecimal mensualite,
                        BigDecimal sousTotal, BigDecimal remisePourcent, BigDecimal remise,
                        BigDecimal montant, String devise) {
    }

    public record Declaration(@NotNull Long formuleId, @NotNull Periodicite periodicite,
                              List<ModuleApplicatif> modules,
                              @NotNull MoyenPaiementAbonnement moyen,
                              @Size(max = 100) String reference,
                              @Size(max = 500) String note) {
    }

    /** Paiement saisi par le super-administrateur ; montant nul ou absent : celui du devis. */
    public record Saisie(@NotNull Long formuleId, @NotNull Periodicite periodicite,
                         List<ModuleApplicatif> modules,
                         @NotNull MoyenPaiementAbonnement moyen,
                         @Size(max = 100) String reference,
                         @Size(max = 500) String note,
                         @PositiveOrZero BigDecimal montant) {
    }

    public record PaiementVue(Long id, Long entrepriseId, String entrepriseNom, Long formuleId, String formuleNom,
                              String periodicite, String periodiciteLibelle, List<String> modules,
                              /** Prix mensuel de chaque module au moment du paiement. */
                              List<Ligne> lignes, BigDecimal montant, String devise, String moyen,
                              String moyenLibelle, String reference, String note, boolean preuve, String preuveNom,
                              String statut, String motifRejet, boolean saisiPlateforme, String traitePar,
                              Instant traiteLe, LocalDate periodeDebut, LocalDate periodeFin,
                              String numeroFacture, Instant creeLe, String creePar) {
    }

    /** Ce que voit l'entreprise pour choisir : formules ouvertes avec leurs prix par module, remises. */
    public record Offre(List<FormuleVue> formules, ParametresVue parametres,
                        /** Vrai si le paiement en ligne (PayGate) est configure sur la plateforme. */
                        boolean paiementEnLigne) {
    }

    /** Paiement en ligne : la formule choisie, et le telephone mobile money qui paie. */
    public record DemandeEnLigne(@NotNull Long formuleId, @NotNull Periodicite periodicite,
                                 List<ModuleApplicatif> modules,
                                 @NotNull @jakarta.validation.constraints.Pattern(regexp = "^(FLOOZ|TMONEY)$") String reseau,
                                 @NotNull @Size(max = 20) String telephone) {
    }

    // ============================================================ reglages

    @Transactional(readOnly = true)
    public ParametresAbonnement parametres() {
        return parametresRepository.findById(1L).orElseGet(ParametresAbonnement::new);
    }

    @Transactional(readOnly = true)
    public ParametresVue parametresVue() {
        return vue(parametres());
    }

    public ParametresVue modifierParametres(ParametresRequete r) {
        ParametresAbonnement p = parametresRepository.findById(1L)
                .orElseThrow(() -> new BusinessException("Reglages des abonnements introuvables"));
        p.setDevise(r.devise().trim());
        p.setDureeEssaiJours(r.dureeEssaiJours());
        p.setJoursGrace(r.joursGrace());
        p.setJoursRappel(r.joursRappel());
        p.setRemiseTrimestrielle(r.remiseTrimestrielle());
        p.setRemiseSemestrielle(r.remiseSemestrielle());
        p.setRemiseAnnuelle(r.remiseAnnuelle());
        p.setInstructionsPaiement(vide(r.instructionsPaiement()));
        p.setMentionsFacture(vide(r.mentionsFacture()));
        return vue(p);
    }

    // ============================================================ formules

    @Transactional(readOnly = true)
    public List<FormuleVue> formules(boolean seulementActives) {
        Map<Long, Long> abonnes = abonnements.findAll().stream()
                .filter(a -> !a.isEssai() && a.getFormule() != null)
                .collect(Collectors.groupingBy(a -> a.getFormule().getId(), Collectors.counting()));
        return formules.findAllByOrderByOrdreAscNomAsc().stream()
                .filter(f -> !seulementActives || f.isActive())
                .map(f -> vue(f, abonnes.getOrDefault(f.getId(), 0L)))
                .toList();
    }

    public FormuleVue creerFormule(FormuleRequete r) {
        if (formules.existsByNomIgnoreCase(r.nom().trim())) {
            throw new BusinessException("Une formule porte deja le nom " + r.nom().trim());
        }
        Formule f = new Formule();
        appliquer(f, r);
        Formule enregistree = formules.save(f);
        tracerPrix(enregistree, Map.of());
        return vue(enregistree, 0);
    }

    public FormuleVue modifierFormule(Long id, FormuleRequete r) {
        Formule f = formule(id);
        if (formules.existsByNomIgnoreCaseAndIdNot(r.nom().trim(), id)) {
            throw new BusinessException("Une formule porte deja le nom " + r.nom().trim());
        }
        Map<ModuleApplicatif, BigDecimal> prixAvant = new EnumMap<>(ModuleApplicatif.class);
        prixAvant.putAll(f.getPrix());
        appliquer(f, r);
        tracerPrix(f, prixAvant);
        return vue(f, abonnements.countByFormuleId(id));
    }

    /**
     * Les prix par module sont une collection, que le journal automatique ne compare pas :
     * leur changement est trace ici, au journal general de la plateforme, apres validation.
     */
    private void tracerPrix(Formule f, Map<ModuleApplicatif, BigDecimal> avant) {
        Map<String, String> anciens = new java.util.LinkedHashMap<>();
        Map<String, String> nouveaux = new java.util.LinkedHashMap<>();
        Set<ModuleApplicatif> modules = EnumSet.noneOf(ModuleApplicatif.class);
        modules.addAll(avant.keySet());
        modules.addAll(f.getPrix().keySet());
        for (ModuleApplicatif m : modules) {
            BigDecimal a = avant.get(m);
            BigDecimal b = f.getPrix().get(m);
            if (a == null ? b != null : b == null || a.compareTo(b) != 0) {
                anciens.put("prix " + m.name(), a == null ? null : a.stripTrailingZeros().toPlainString());
                nouveaux.put("prix " + m.name(), b == null ? null : b.stripTrailingZeros().toPlainString());
            }
        }
        if (nouveaux.isEmpty()) {
            return;
        }
        boolean creation = avant.isEmpty();
        String detail = nouveaux.keySet().stream()
                .map(k -> k.substring(5) + " : " + (anciens.get(k) == null ? "—" : anciens.get(k)) + " → "
                        + (nouveaux.get(k) == null ? "retire" : nouveaux.get(k)))
                .collect(Collectors.joining(", "));
        journal.plateforme(creation ? "CREATION" : "MODIFICATION", "Formule", "Formule d'abonnement", f.getId(),
                "Prix de la formule « " + f.getNom() + " » " + (creation ? "fixes" : "modifies") + " (" + detail + ")",
                creation ? null : anciens, nouveaux);
    }

    /** Une formule deja utilisee ne se supprime pas : on la desactive pour ne plus la proposer. */
    public void supprimerFormule(Long id) {
        Formule f = formule(id);
        if (abonnements.countByFormuleId(id) > 0 || paiements.countByFormuleId(id) > 0) {
            throw new BusinessException("La formule " + f.getNom()
                    + " a deja servi : desactivez-la plutot pour ne plus la proposer");
        }
        formules.delete(f);
    }

    @Transactional(readOnly = true)
    public Offre offre() {
        return new Offre(formules(true), parametresVue(), payGate.actif());
    }

    // ======================================================== abonnements

    /** Ouvre l'essai gratuit d'une nouvelle entreprise ; jours nuls : la duree reglee. */
    public void ouvrirEssai(Long entrepriseId, Integer jours) {
        if (abonnements.findByEntrepriseId(entrepriseId).isPresent()) {
            return;
        }
        // Duree 0 : pas d'essai, l'entreprise entre directement dans la grace et doit payer.
        int duree = Math.max(0, jours == null ? parametres().getDureeEssaiJours() : jours);
        Abonnement a = new Abonnement();
        a.setEntrepriseId(entrepriseId);
        a.setEssai(true);
        a.setDebut(LocalDate.now());
        a.setFin(LocalDate.now().plusDays(duree - 1L));
        abonnements.save(a);
    }

    /** Abonnement de l'entreprise ; une entreprise sans ligne recoit son essai. */
    public Abonnement abonnement(Long entrepriseId) {
        return abonnements.findByEntrepriseId(entrepriseId).orElseGet(() -> {
            ouvrirEssai(entrepriseId, null);
            return abonnements.findByEntrepriseId(entrepriseId).orElseThrow();
        });
    }

    public EtatAbonnement etat(Abonnement a, ParametresAbonnement p, LocalDate jour) {
        if (!jour.isAfter(a.getFin())) {
            if (a.isEssai()) {
                return EtatAbonnement.ESSAI;
            }
            return ChronoUnit.DAYS.between(jour, a.getFin()) < p.getJoursRappel()
                    ? EtatAbonnement.A_RENOUVELER : EtatAbonnement.ACTIF;
        }
        return jour.isAfter(a.getFin().plusDays(p.getJoursGrace())) ? EtatAbonnement.EXPIRE : EtatAbonnement.GRACE;
    }

    /** L'entreprise ne peut plus que consulter : grace epuisee. Sans abonnement connu : non. */
    @Transactional(readOnly = true)
    public boolean lectureSeule(Long entrepriseId) {
        return abonnements.findByEntrepriseId(entrepriseId)
                .map(a -> etat(a, parametres(), LocalDate.now()) == EtatAbonnement.EXPIRE)
                .orElse(false);
    }

    public EtatCourt etatCourt(Long entrepriseId) {
        Abonnement a = abonnement(entrepriseId);
        ParametresAbonnement p = parametres();
        LocalDate jour = LocalDate.now();
        EtatAbonnement etat = etat(a, p, jour);
        boolean rappel = etat == EtatAbonnement.A_RENOUVELER || etat == EtatAbonnement.GRACE
                || etat == EtatAbonnement.EXPIRE
                || (etat == EtatAbonnement.ESSAI && ChronoUnit.DAYS.between(jour, a.getFin()) < p.getJoursRappel());
        return new EtatCourt(etat.name(), etat.getLibelle(), a.isEssai(), etat == EtatAbonnement.EXPIRE, rappel,
                a.getFin(), a.getFin().plusDays(p.getJoursGrace()), ChronoUnit.DAYS.between(jour, a.getFin()),
                a.getFormule() == null ? null : a.getFormule().getNom());
    }

    public AbonnementVue vueAbonnement(Long entrepriseId) {
        Abonnement a = abonnement(entrepriseId);
        Map<Ressource, Long> utilises = new EnumMap<>(Ressource.class);
        for (Ressource r : Ressource.values()) {
            utilises.put(r, compter(r, entrepriseId));
        }
        String nom = entreprises.findById(entrepriseId).map(Entreprise::getNom).orElse(null);
        return vue(a, nom, parametres(), utilises,
                paiements.existsByEntrepriseIdAndStatut(entrepriseId, StatutPaiementAbonnement.EN_ATTENTE),
                paiementsEnVigueur().get(entrepriseId));
    }

    /** Abonnements de toutes les entreprises, pour la console (cinq requetes de comptage en tout). */
    public List<AbonnementVue> tousLesAbonnements() {
        List<Entreprise> liste = entreprises.findAll();
        Map<Long, Abonnement> parEntreprise = abonnements.findAll().stream()
                .collect(Collectors.toMap(Abonnement::getEntrepriseId, Function.identity()));
        Map<Ressource, Map<Long, Long>> comptes = new EnumMap<>(Ressource.class);
        for (Ressource r : Ressource.values()) {
            comptes.put(r, compterParEntreprise(r));
        }
        Set<Long> enAttente = paiements.findAll().stream()
                .filter(p -> p.getStatut() == StatutPaiementAbonnement.EN_ATTENTE)
                .map(PaiementAbonnement::getEntrepriseId).collect(Collectors.toSet());
        ParametresAbonnement p = parametres();
        Map<Long, PaiementAbonnement> enVigueur = paiementsEnVigueur();
        List<AbonnementVue> vues = new ArrayList<>();
        for (Entreprise e : liste) {
            Abonnement a = parEntreprise.get(e.getId());
            if (a == null) {
                a = abonnement(e.getId());
            }
            Map<Ressource, Long> utilises = new EnumMap<>(Ressource.class);
            for (Ressource r : Ressource.values()) {
                utilises.put(r, comptes.get(r).getOrDefault(e.getId(), 0L));
            }
            vues.add(vue(a, e.getNom(), p, utilises, enAttente.contains(e.getId()), enVigueur.get(e.getId())));
        }
        return vues;
    }

    /** Geste commercial : prolonge l'echeance (essai compris) sans paiement. */
    public AbonnementVue prolonger(Long entrepriseId, int jours) {
        if (jours < 1 || jours > 730) {
            throw new BusinessException("Prolongation de 1 a 730 jours");
        }
        Abonnement a = abonnement(entrepriseId);
        LocalDate base = a.getFin().isBefore(LocalDate.now()) ? LocalDate.now().minusDays(1) : a.getFin();
        a.setFin(base.plusDays(jours));
        return vueAbonnement(entrepriseId);
    }

    // ============================================================= devis

    public Devis devis(DemandeDevis d) {
        return devis(formuleOuverte(d.formuleId()), d.periodicite(), d.modules());
    }

    private Devis devis(Formule f, Periodicite periodicite, List<ModuleApplicatif> demandes) {
        Set<ModuleApplicatif> modules = demandes == null ? EnumSet.noneOf(ModuleApplicatif.class)
                : demandes.stream().filter(ModuleApplicatif::isDisponible)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(ModuleApplicatif.class)));
        if (modules.isEmpty()) {
            throw new BusinessException("Choisissez au moins un module");
        }
        // Un module que la formule ne tarifie pas n'y est pas propose : jamais facture 0 par defaut.
        modules.stream().filter(m -> f.getPrix() == null || !f.getPrix().containsKey(m)).findFirst()
                .ifPresent(m -> {
                    throw new BusinessException("Le module " + m.getLibelle() + " n'est pas propose dans la formule "
                            + f.getNom());
                });
        // Chaque module se paie a son prix dans la formule choisie.
        List<Ligne> lignes = modules.stream()
                .map(m -> new Ligne(m.name(), m.getLibelle(), f.prixDe(m)))
                .toList();
        BigDecimal mensualite = lignes.stream().map(Ligne::prixMensuel).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sousTotal = mensualite.multiply(BigDecimal.valueOf(periodicite.getMois()));
        ParametresAbonnement p = parametres();
        BigDecimal pourcent = p.remise(periodicite);
        BigDecimal remise = sousTotal.multiply(pourcent).divide(CENT, 0, RoundingMode.HALF_UP);
        return new Devis(f.getId(), f.getNom(), periodicite.name(), periodicite.getMois(),
                modules.stream().map(Enum::name).toList(), lignes, mensualite, sousTotal, pourcent, remise,
                sousTotal.subtract(remise), p.getDevise());
    }

    // ========================================================== paiements

    /** L'entreprise declare un paiement ; il attend la validation du super-administrateur. */
    public PaiementVue declarer(Long entrepriseId, Declaration d) {
        if (paiements.existsByEntrepriseIdAndStatut(entrepriseId, StatutPaiementAbonnement.EN_ATTENTE)) {
            throw new BusinessException("Un paiement attend deja la validation : annulez-le pour en declarer un autre");
        }
        if (d.moyen() == MoyenPaiementAbonnement.PAYGATE) {
            throw new BusinessException("Le paiement en ligne se fait par le bouton Payer en ligne");
        }
        Formule f = formuleOuverte(d.formuleId());
        Devis devis = devis(f, d.periodicite(), d.modules());
        if (devis.montant().signum() <= 0) {
            throw new BusinessException("Le montant a payer est nul : choisissez des modules tarifes");
        }
        exigerReferenceInedite(d.moyen() == null ? null : d.moyen().toString(), d.reference());
        PaiementAbonnement p = nouveauPaiement(entrepriseId, f, d.periodicite(), devis, d.moyen(),
                d.reference(), d.note(), devis.montant());
        return vue(paiements.save(p), nomEntreprise(entrepriseId));
    }

    // ------------------------------------------------- paiement en ligne (PayGate)

    /** Plus haut montant envoye en une fois a PayGate (plafond des portefeuilles mobile money). */
    static final long MONTANT_MAX_EN_LIGNE = 1_000_000;

    /**
     * Prepare un paiement en ligne : meme devis qu'une declaration, enregistre en attente avec
     * un identifiant aleatoire. La demande part ensuite chez PayGate, hors de cette transaction.
     */
    public PaiementAbonnement preparerPaiementEnLigne(Long entrepriseId, DemandeEnLigne d, String telephone) {
        if (paiements.existsByEntrepriseIdAndStatut(entrepriseId, StatutPaiementAbonnement.EN_ATTENTE)) {
            throw new BusinessException("Un paiement attend deja sa confirmation : patientez ou annulez-le");
        }
        Formule f = formuleOuverte(d.formuleId());
        Devis devis = devis(f, d.periodicite(), d.modules());
        BigDecimal montant = devis.montant();
        if (montant.signum() <= 0) {
            throw new BusinessException("Le montant a payer est nul : choisissez des modules tarifes");
        }
        if (montant.stripTrailingZeros().scale() > 0 || montant.compareTo(BigDecimal.valueOf(MONTANT_MAX_EN_LIGNE)) > 0) {
            throw new BusinessException("Ce montant ne peut pas etre paye en ligne en une fois : choisissez une"
                    + " periode plus courte ou declarez un virement");
        }
        PaiementAbonnement p = nouveauPaiement(entrepriseId, f, d.periodicite(), devis, MoyenPaiementAbonnement.PAYGATE,
                null, null, montant);
        String identifiant = "ABO" + java.util.HexFormat.of().formatHex(aleatoire(12)).toUpperCase();
        p.setPaygateIdentifiant(identifiant);
        p.setReference(identifiant);
        p.setPaygateReseau(d.reseau());
        p.setPaygateTelephone(telephone);
        return paiements.saveAndFlush(p);
    }

    /** PayGate a enregistre la demande : le client la valide maintenant sur son telephone. */
    public PaiementVue demandeEnvoyee(Long id, String txReference) {
        PaiementAbonnement p = paiement(id);
        p.setPaygateTxReference(txReference);
        return vue(p, nomEntreprise(p.getEntrepriseId()));
    }

    /** PayGate a refuse la demande : le paiement est rejete, l'entreprise peut recommencer. */
    public void demandeRefusee(Long id, String motif) {
        PaiementAbonnement p = paiement(id);
        p.setStatut(StatutPaiementAbonnement.REJETE);
        p.setMotifRejet(motif);
        p.setTraitePar("PayGate");
        p.setTraiteLe(Instant.now());
    }

    /**
     * Applique l'etat verifie aupres de PayGate (jamais celui d'un rappel non signe).
     * Paye : l'abonnement s'ouvre comme apres une validation du super-administrateur.
     * Un paiement rejete puis confirme paye (debit tardif) est quand meme valide : l'argent est recu.
     */
    public PaiementVue appliquerEtatPayGate(String identifiant,
                                            tg.parcauto.api.plateforme.abonnement.paygate.PayGateClient.Etat etat) {
        PaiementAbonnement p = paiements.verrouillerPayGate(identifiant)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement en ligne introuvable"));
        p.setPaygateVerifieLe(Instant.now());
        if (etat.txReference() != null && p.getPaygateTxReference() == null) {
            p.setPaygateTxReference(etat.txReference());
        }
        boolean traitable = p.getStatut() == StatutPaiementAbonnement.EN_ATTENTE
                || (p.getStatut() == StatutPaiementAbonnement.REJETE && "PayGate".equals(p.getTraitePar()));
        if (!traitable) {
            return vue(p, nomEntreprise(p.getEntrepriseId()));
        }
        switch (etat.statut()) {
            case tg.parcauto.api.plateforme.abonnement.paygate.PayGateClient.PAYE -> {
                p.setPaygateReferencePaiement(etat.referencePaiement());
                p.setMotifRejet(null);
                appliquer(p, "PayGate (paiement en ligne)");
                journal.plateforme("PAIEMENT_EN_LIGNE", "PaiementAbonnement", p.getNumeroFacture(), p.getId(),
                        "Paiement en ligne " + identifiant + " confirme par PayGate ("
                                + p.getMontant().toPlainString() + " " + p.getDevise() + ")", null, null);
            }
            case tg.parcauto.api.plateforme.abonnement.paygate.PayGateClient.EXPIRE,
                 tg.parcauto.api.plateforme.abonnement.paygate.PayGateClient.ANNULE -> {
                if (p.getStatut() == StatutPaiementAbonnement.EN_ATTENTE) {
                    p.setStatut(StatutPaiementAbonnement.REJETE);
                    p.setMotifRejet(etat.statut() == tg.parcauto.api.plateforme.abonnement.paygate.PayGateClient.EXPIRE
                            ? "Paiement non valide a temps sur le telephone (expire)" : "Paiement annule");
                    p.setTraitePar("PayGate");
                    p.setTraiteLe(Instant.now());
                }
            }
            default -> {
                // En cours : rien a faire, la verification reprendra.
            }
        }
        return vue(p, nomEntreprise(p.getEntrepriseId()));
    }

    /** Paiement en ligne de l'entreprise, pour verifier son etat depuis l'ecran. */
    @Transactional(readOnly = true)
    public PaiementAbonnement paiementEnLigneDe(Long entrepriseId, Long id) {
        PaiementAbonnement p = paiementDe(entrepriseId, id);
        if (p.getMoyen() != MoyenPaiementAbonnement.PAYGATE || p.getPaygateIdentifiant() == null) {
            throw new BusinessException("Ce paiement n'a pas ete fait en ligne");
        }
        return p;
    }

    @Transactional(readOnly = true)
    public PaiementVue vuePaiement(Long id) {
        PaiementAbonnement p = paiement(id);
        return vue(p, nomEntreprise(p.getEntrepriseId()));
    }

    private static byte[] aleatoire(int octets) {
        byte[] b = new byte[octets];
        new java.security.SecureRandom().nextBytes(b);
        return b;
    }

    /** Le super-administrateur saisit un paiement recu : il est valide aussitot. */
    public PaiementVue enregistrer(Long entrepriseId, Saisie s, String auteur) {
        if (!entreprises.existsById(entrepriseId)) {
            throw ResourceNotFoundException.of("Entreprise", entrepriseId);
        }
        // Une declaration en attente est sans doute ce meme paiement : la saisir en plus le compterait deux fois.
        if (paiements.existsByEntrepriseIdAndStatut(entrepriseId, StatutPaiementAbonnement.EN_ATTENTE)) {
            throw new BusinessException("L'entreprise a declare un paiement qui attend votre decision :"
                    + " validez-le ou rejetez-le dans Abonnements > Paiements avant d'en saisir un autre");
        }
        Formule f = formule(s.formuleId());
        Devis devis = devis(f, s.periodicite(), s.modules());
        boolean sansMontant = s.montant() == null || s.montant().signum() == 0;
        PaiementAbonnement p = nouveauPaiement(entrepriseId, f, s.periodicite(), devis, s.moyen(),
                s.reference(), s.note(), sansMontant ? devis.montant() : s.montant());
        p.setSaisiPlateforme(true);
        paiements.save(p);
        appliquer(p, auteur);
        return vue(p, nomEntreprise(entrepriseId));
    }

    public PaiementVue valider(Long id, String auteur) {
        PaiementAbonnement p = paiement(id);
        if (p.getStatut() != StatutPaiementAbonnement.EN_ATTENTE) {
            throw new BusinessException("Ce paiement a deja ete traite");
        }
        appliquer(p, auteur);
        return vue(p, nomEntreprise(p.getEntrepriseId()));
    }

    public PaiementVue rejeter(Long id, String motif, String auteur) {
        PaiementAbonnement p = paiement(id);
        if (p.getStatut() != StatutPaiementAbonnement.EN_ATTENTE) {
            throw new BusinessException("Ce paiement a deja ete traite");
        }
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Indiquez le motif du rejet : l'entreprise le verra");
        }
        p.setStatut(StatutPaiementAbonnement.REJETE);
        p.setMotifRejet(motif.trim().length() > 300 ? motif.trim().substring(0, 300) : motif.trim());
        p.setTraitePar(auteur);
        p.setTraiteLe(Instant.now());
        return vue(p, nomEntreprise(p.getEntrepriseId()));
    }

    /** L'entreprise retire une declaration encore en attente. */
    public void annulerDeclaration(Long entrepriseId, Long id) {
        PaiementAbonnement p = paiementDe(entrepriseId, id);
        if (p.getStatut() != StatutPaiementAbonnement.EN_ATTENTE) {
            throw new BusinessException("Seul un paiement en attente peut etre annule");
        }
        if (p.getMoyen() == MoyenPaiementAbonnement.PAYGATE) {
            throw new BusinessException("Un paiement en ligne ne s'annule pas ici : s'il n'est pas valide sur le"
                    + " telephone, il expire de lui-meme et vous pourrez recommencer");
        }
        if (p.getPreuveChemin() != null) {
            stockage.supprimer(p.getPreuveChemin());
        }
        paiements.delete(p);
    }

    public PaiementVue joindrePreuve(Long entrepriseId, Long id, MultipartFile fichier) {
        PaiementAbonnement p = paiementDe(entrepriseId, id);
        if (p.getStatut() != StatutPaiementAbonnement.EN_ATTENTE) {
            throw new BusinessException("La preuve se joint tant que le paiement est en attente");
        }
        if (fichier.getSize() > 15L * 1024 * 1024) {
            throw new BusinessException("La preuve depasse 15 Mo");
        }
        String nom = fichier.getOriginalFilename() == null ? "preuve" : fichier.getOriginalFilename();
        String type = typePreuve(nom);
        if (type == null) {
            throw new BusinessException("La preuve doit etre un PDF ou une image (JPG, PNG, WEBP)");
        }
        String ancien = p.getPreuveChemin();
        p.setPreuveChemin(stockage.enregistrer(fichier, "abonnements/" + entrepriseId));
        p.setPreuveNom(nom);
        p.setPreuveType(type);
        if (ancien != null) {
            stockage.supprimer(ancien);
        }
        return vue(p, nomEntreprise(entrepriseId));
    }

    /**
     * Type de la preuve tire de son extension, jamais de celui annonce par le
     * navigateur : seul un PDF ou une image peut s'afficher dans la console.
     */
    static String typePreuve(String nom) {
        String n = nom == null ? "" : nom.toLowerCase(java.util.Locale.ROOT);
        if (n.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (n.endsWith(".png")) {
            return "image/png";
        }
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (n.endsWith(".webp")) {
            return "image/webp";
        }
        return null;
    }

    /** Preuve d'un paiement ; entreprise nulle pour le super-administrateur. */
    @Transactional(readOnly = true)
    public PaiementAbonnement paiementAvecPreuve(Long entrepriseId, Long id) {
        PaiementAbonnement p = entrepriseId == null ? paiement(id) : paiementDe(entrepriseId, id);
        if (p.getPreuveChemin() == null) {
            throw new ResourceNotFoundException("Aucune preuve jointe a ce paiement");
        }
        return p;
    }

    @Transactional(readOnly = true)
    public Resource preuve(PaiementAbonnement p) {
        return stockage.charger(p.getPreuveChemin());
    }

    @Transactional(readOnly = true)
    public List<PaiementVue> paiementsDe(Long entrepriseId) {
        String nom = nomEntreprise(entrepriseId);
        return paiements.findByEntrepriseIdOrderByCreeLeDesc(entrepriseId).stream().map(p -> vue(p, nom)).toList();
    }

    @Transactional(readOnly = true)
    public List<PaiementVue> tousLesPaiements() {
        Map<Long, String> noms = entreprises.findAll().stream()
                .collect(Collectors.toMap(Entreprise::getId, Entreprise::getNom));
        return paiements.findAllByOrderByCreeLeDesc().stream()
                .map(p -> vue(p, noms.get(p.getEntrepriseId()))).toList();
    }

    // ===================================================== garde-fous metier

    /** Une reference de virement ou de mobile money deja declaree (et non rejetee) ne sert pas deux fois. */
    private void exigerReferenceInedite(String moyen, String reference) {
        if (moyen == null || reference == null || reference.isBlank()) {
            return;
        }
        Integer deja = jdbc.queryForObject("""
                select count(*) from abo_paiement
                where moyen = ? and lower(reference) = lower(?) and statut <> 'REJETE'
                """, Integer.class, moyen, reference.trim());
        if (deja != null && deja > 0) {
            throw new BusinessException("Cette reference de paiement a deja ete declaree");
        }
    }

    /**
     * Refuse la creation au-dela de la limite de la formule de l'entreprise
     * courante. Pendant l'essai, ou sans formule, rien n'est limite.
     */
    @Transactional(readOnly = true)
    public void verifierLimite(Ressource ressource) {
        Long entrepriseId = ContexteEntreprise.courante().orElse(null);
        if (entrepriseId == null) {
            return;
        }
        abonnements.findByEntrepriseId(entrepriseId)
                .filter(a -> !a.isEssai() && a.getFormule() != null)
                .ifPresent(a -> {
                    Integer limite = a.getFormule().limite(ressource);
                    if (limite != null) {
                        // Verrou par entreprise et par ressource jusqu'a la fin de la transaction de
                        // l'appelant : deux creations simultanees ne depassent pas la limite payee.
                        jdbc.query("select pg_advisory_xact_lock(?, ?)", rs -> null,
                                entrepriseId.intValue(), ressource.ordinal() + 1000);
                    }
                    if (limite != null && compter(ressource, entrepriseId) >= limite) {
                        throw new BusinessException("Votre formule " + a.getFormule().getNom() + " est limitee a "
                                + limite + " " + ressource.getLibelle().toLowerCase()
                                + " : passez a une formule superieure depuis Mon abonnement");
                    }
                });
    }

    /** Une fois l'abonnement paye, seuls les modules payes (compris ou en option) s'activent. */
    @Transactional(readOnly = true)
    public void verifierModuleAutorise(Long entrepriseId, ModuleApplicatif module) {
        abonnements.findByEntrepriseId(entrepriseId)
                .filter(a -> !a.isEssai())
                .filter(a -> !a.modulesPayes().contains(module))
                .ifPresent(a -> {
                    throw new BusinessException("Le module " + module.getLibelle()
                            + " ne fait pas partie de votre abonnement : ajoutez-le depuis Mon abonnement");
                });
    }

    // ============================================================ interne

    private PaiementAbonnement nouveauPaiement(Long entrepriseId, Formule f, Periodicite periodicite, Devis devis,
                                               MoyenPaiementAbonnement moyen, String reference, String note,
                                               BigDecimal montant) {
        PaiementAbonnement p = new PaiementAbonnement();
        p.setEntrepriseId(entrepriseId);
        p.setFormule(f);
        p.setFormuleNom(f.getNom());
        p.setPeriodicite(periodicite);
        p.setModules(String.join(",", devis.modules()));
        p.setDetail(devis.lignes().stream().map(l -> l.module() + "=" + l.prixMensuel().toPlainString())
                .collect(Collectors.joining(";")));
        p.setMontant(montant);
        p.setDevise(devis.devise());
        p.setMoyen(moyen);
        p.setReference(vide(reference));
        p.setNote(vide(note));
        p.setStatut(StatutPaiementAbonnement.EN_ATTENTE);
        return p;
    }

    /**
     * Valide le paiement : la periode suit l'echeance en cours si l'abonnement
     * paye court encore, sinon elle part d'aujourd'hui. Formule, limites et
     * modules ne changent qu'au debut de la periode payee : un renouvellement
     * anticipe laisse l'entreprise profiter jusqu'au bout de ce qu'elle a deja paye.
     */
    private void appliquer(PaiementAbonnement p, String auteur) {
        Abonnement a = abonnement(p.getEntrepriseId());
        LocalDate jour = LocalDate.now();
        // Paye pendant l'essai ou avant l'echeance : la periode payee commence apres, rien n'est perdu.
        boolean continuite = !a.getFin().isBefore(jour);
        LocalDate debut = continuite ? a.getFin().plusDays(1) : jour;
        LocalDate fin = debut.plusMonths(p.getPeriodicite().getMois()).minusDays(1);

        p.setStatut(StatutPaiementAbonnement.VALIDE);
        p.setTraitePar(auteur);
        p.setTraiteLe(Instant.now());
        p.setPeriodeDebut(debut);
        p.setPeriodeFin(fin);
        Long numero = jdbc.queryForObject("SELECT nextval('abo_facture_seq')", Long.class);
        p.setNumeroFacture("FA-" + jour.getYear() + "-" + String.format("%05d", numero));

        boolean etaitEssai = a.isEssai();
        if (!continuite) {
            a.setDebut(debut);
        }
        a.setFin(fin);
        a.setEssai(false);
        // Pendant un essai, rien de paye n'est a proteger : la formule s'applique aussitot,
        // et la periode payee commence quand meme apres l'essai.
        if (!debut.isAfter(jour) || etaitEssai) {
            mettreEnVigueur(a, p);
        }
    }

    /**
     * Chaque nuit (et au demarrage), les paiements valides dont la periode
     * commence prennent effet : formule, periodicite et modules payes.
     */
    public int basculerPeriodes() {
        LocalDate jour = LocalDate.now();
        Map<Long, PaiementAbonnement> enVigueur = new HashMap<>();
        for (PaiementAbonnement p : paiements.findByStatutAndPeriodeDebutLessThanEqualOrderByPeriodeDebutDescIdDesc(
                StatutPaiementAbonnement.VALIDE, jour)) {
            enVigueur.putIfAbsent(p.getEntrepriseId(), p);
        }
        int n = 0;
        for (PaiementAbonnement p : enVigueur.values()) {
            Abonnement a = abonnements.findByEntrepriseId(p.getEntrepriseId()).orElse(null);
            if (a == null || a.isEssai()) {
                continue;
            }
            boolean memeFormule = a.getFormule() != null && p.getFormule() != null
                    && a.getFormule().getId().equals(p.getFormule().getId());
            if (!memeFormule || a.getPeriodicite() != p.getPeriodicite()
                    || !a.modulesPayes().equals(p.modulesListe())) {
                mettreEnVigueur(a, p);
                n++;
            }
        }
        return n;
    }

    private void mettreEnVigueur(Abonnement a, PaiementAbonnement p) {
        a.setFormule(p.getFormule());
        a.setPeriodicite(p.getPeriodicite());
        a.setModules(p.getModules());

        Set<ModuleApplicatif> payes = p.modulesListe();
        for (ModuleApplicatif m : payes) {
            moduleService.activer(p.getEntrepriseId(), m);
        }
        for (ModuleApplicatif m : moduleService.actifs(p.getEntrepriseId())) {
            if (!payes.contains(m)) {
                moduleService.desactiver(p.getEntrepriseId(), m);
            }
        }
    }

    public long compter(Ressource r, Long entrepriseId) {
        Long n = jdbc.queryForObject(r.requeteComptage(), Long.class, entrepriseId);
        return n == null ? 0 : n;
    }

    public Map<Long, Long> compterParEntreprise(Ressource r) {
        Map<Long, Long> comptes = new HashMap<>();
        jdbc.query(r.requeteParEntreprise(), rs -> {
            comptes.put(rs.getLong(1), rs.getLong(2));
        });
        return comptes;
    }

    private Formule formule(Long id) {
        return formules.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Formule", id));
    }

    private Formule formuleOuverte(Long id) {
        Formule f = formule(id);
        if (!f.isActive()) {
            throw new BusinessException("La formule " + f.getNom() + " n'est plus proposee");
        }
        return f;
    }

    private PaiementAbonnement paiement(Long id) {
        return paiements.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Paiement", id));
    }

    private PaiementAbonnement paiementDe(Long entrepriseId, Long id) {
        PaiementAbonnement p = paiement(id);
        if (!p.getEntrepriseId().equals(entrepriseId)) {
            throw ResourceNotFoundException.of("Paiement", id);
        }
        return p;
    }

    private String nomEntreprise(Long id) {
        return entreprises.findById(id).map(Entreprise::getNom).orElse(null);
    }

    private void appliquer(Formule f, FormuleRequete r) {
        f.setNom(r.nom().trim());
        f.setDescription(vide(r.description()));
        Map<ModuleApplicatif, BigDecimal> prix = new EnumMap<>(ModuleApplicatif.class);
        r.prix().forEach((m, v) -> {
            if (m != null && v != null) {
                prix.put(m, v);
            }
        });
        f.getPrix().clear();
        f.getPrix().putAll(prix);
        f.setMaxUtilisateurs(r.maxUtilisateurs());
        f.setMaxVehicules(r.maxVehicules());
        f.setMaxAgences(r.maxAgences());
        f.setMaxCaisses(r.maxCaisses());
        f.setMaxEmployes(r.maxEmployes());
        f.setActive(r.active());
        f.setMiseEnAvant(r.miseEnAvant());
        f.setOrdre(r.ordre());
    }

    private FormuleVue vue(Formule f, long abonnes) {
        Map<String, BigDecimal> prix = new java.util.LinkedHashMap<>();
        Arrays.stream(ModuleApplicatif.values()).filter(ModuleApplicatif::isDisponible)
                .forEach(m -> prix.put(m.name(), f.prixDe(m)));
        return new FormuleVue(f.getId(), f.getNom(), f.getDescription(), prix,
                f.getMaxUtilisateurs(), f.getMaxVehicules(), f.getMaxAgences(), f.getMaxCaisses(),
                f.getMaxEmployes(), f.isActive(), f.isMiseEnAvant(), f.getOrdre(), abonnes);
    }

    private ParametresVue vue(ParametresAbonnement p) {
        return new ParametresVue(p.getDevise(), p.getDureeEssaiJours(), p.getJoursGrace(), p.getJoursRappel(),
                p.getRemiseTrimestrielle(), p.getRemiseSemestrielle(), p.getRemiseAnnuelle(),
                p.getInstructionsPaiement(), p.getMentionsFacture());
    }

    /** Dernier paiement valide dont la periode a commence, par entreprise. */
    private Map<Long, PaiementAbonnement> paiementsEnVigueur() {
        Map<Long, PaiementAbonnement> parEntreprise = new HashMap<>();
        for (PaiementAbonnement p : paiements.findByStatutAndPeriodeDebutLessThanEqualOrderByPeriodeDebutDescIdDesc(
                StatutPaiementAbonnement.VALIDE, LocalDate.now())) {
            parEntreprise.putIfAbsent(p.getEntrepriseId(), p);
        }
        return parEntreprise;
    }

    /**
     * Mensualite reellement payee : le montant du paiement en vigueur ramene au
     * mois (remise de periodicite et montant saisi compris). Changer le prix
     * d'une formule ne touche donc pas les abonnes avant leur renouvellement.
     * Sans paiement connu, le prix actuel des modules de la formule.
     */
    private BigDecimal mensualite(Abonnement a, PaiementAbonnement paye) {
        if (a.isEssai() || a.getFormule() == null) {
            return BigDecimal.ZERO;
        }
        if (paye != null && paye.getMontant() != null && paye.getPeriodicite() != null) {
            return paye.getMontant().divide(BigDecimal.valueOf(paye.getPeriodicite().getMois()), 0, RoundingMode.HALF_UP);
        }
        Formule f = a.getFormule();
        return a.modulesPayes().stream().map(f::prixDe).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private AbonnementVue vue(Abonnement a, String nom, ParametresAbonnement p, Map<Ressource, Long> utilises,
                              boolean enAttente, PaiementAbonnement paye) {
        LocalDate jour = LocalDate.now();
        EtatAbonnement etat = etat(a, p, jour);
        Formule f = a.getFormule();
        List<Usage> usages = Arrays.stream(Ressource.values())
                .map(r -> new Usage(r.name(), r.getLibelle(), utilises.getOrDefault(r, 0L),
                        a.isEssai() || f == null ? null : f.limite(r)))
                .toList();
        BigDecimal mensualite = mensualite(a, paye);
        boolean rappel = etat == EtatAbonnement.A_RENOUVELER || etat == EtatAbonnement.GRACE
                || etat == EtatAbonnement.EXPIRE
                || (etat == EtatAbonnement.ESSAI && ChronoUnit.DAYS.between(jour, a.getFin()) < p.getJoursRappel());
        return new AbonnementVue(a.getEntrepriseId(), nom, etat.name(), etat.getLibelle(), a.isEssai(),
                etat == EtatAbonnement.EXPIRE, rappel, a.getDebut(), a.getFin(),
                a.getFin().plusDays(p.getJoursGrace()), ChronoUnit.DAYS.between(jour, a.getFin()),
                f == null ? null : f.getId(), f == null ? null : f.getNom(),
                a.getPeriodicite() == null ? null : a.getPeriodicite().name(),
                a.getPeriodicite() == null ? null : a.getPeriodicite().getLibelle(),
                new ArrayList<>(new LinkedHashSet<>(a.modulesPayes().stream().map(Enum::name).toList())),
                usages, enAttente, mensualite);
    }

    private PaiementVue vue(PaiementAbonnement p, String nom) {
        return new PaiementVue(p.getId(), p.getEntrepriseId(), nom, p.getFormule().getId(), p.getFormuleNom(),
                p.getPeriodicite().name(), p.getPeriodicite().getLibelle(),
                p.modulesListe().stream().map(Enum::name).toList(),
                p.detailPrix().entrySet().stream()
                        .map(e -> new Ligne(e.getKey().name(), e.getKey().getLibelle(), e.getValue())).toList(),
                p.getMontant(), p.getDevise(), p.getMoyen().name(), p.getMoyen().getLibelle(), p.getReference(),
                p.getNote(), p.getPreuveChemin() != null, p.getPreuveNom(), p.getStatut().name(), p.getMotifRejet(),
                p.isSaisiPlateforme(), p.getTraitePar(), p.getTraiteLe(), p.getPeriodeDebut(), p.getPeriodeFin(),
                p.getNumeroFacture(), p.getCreeLe(), p.getCreePar());
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }
}
