package tg.parcauto.api.agence.cloture;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.agence.Agence;
import tg.parcauto.api.agence.AuditAgenceService;
import tg.parcauto.api.agence.DroitsAgence;
import tg.parcauto.api.agence.PerimetreAgence;
import tg.parcauto.api.agence.caisse.SessionCaisseAgence;
import tg.parcauto.api.agence.caisse.SessionCaisseAgenceRepository;
import tg.parcauto.api.agence.tresorerie.CompteAgence;
import tg.parcauto.api.agence.tresorerie.CompteService;
import tg.parcauto.api.agence.tresorerie.EcritureRepository;
import tg.parcauto.api.agence.tresorerie.LigneRegistreRepository;
import tg.parcauto.api.agence.tresorerie.RegistreService;
import tg.parcauto.api.agence.tresorerie.TypeCompte;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cloture journaliere d'une agence.
 * <p>
 * On consulte la journee, on releve le solde reel de chaque compte (cash
 * compte, solde de chaque float chez l'operateur), l'ecart avec le solde
 * theorique du registre se justifie, puis on soumet. Un superviseur ou
 * l'administrateur valide : la journee est verrouillee. Si des operations ont eu
 * lieu entre la soumission et la validation, la cloture est a refaire. Une
 * journee validee ne se rouvre que par l'administrateur, avec un motif trace.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ClotureService {

    private static final DateTimeFormatter JJ_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ClotureAgenceRepository repository;
    private final PerimetreAgence perimetre;
    private final CompteService comptes;
    private final LigneRegistreRepository lignes;
    private final EcritureRepository ecritures;
    private final RegistreService registre;
    private final SessionCaisseAgenceRepository sessions;
    private final AuditAgenceService audit;

    public record LigneSaisie(@NotNull Long compteId, Long soldeReel, @Size(max = 500) String justification) {
    }

    public record Soumission(@NotNull Long agenceId, @NotNull LocalDate journee, @NotNull List<@Valid LigneSaisie> lignes,
                             @Size(max = 1000) String observations) {
    }

    /** Compte a controler : solde theorique a la fin de la journee ; cash et virtuel sont obligatoires. */
    public record CompteAControler(Long compteId, String libelle, String famille, boolean obligatoire, long soldeTheorique) {
    }

    public record Preparation(Long agenceId, String agenceNom, LocalDate journee, LocalDate journeeCourante,
                              long ecritures, List<CompteAControler> comptes, List<String> caissesOuvertes,
                              ClotureDto cloture) {
    }

    public record LigneDto(Long compteId, String libelle, String famille, long soldeTheorique, Long soldeReel, Long ecart,
                           String justification) {
    }

    public record ClotureDto(Long id, Long agenceId, String agenceNom, LocalDate journee, ClotureAgence.Statut statut,
                             String observations, Instant soumiseLe, String soumiseParNom, Instant valideeLe,
                             String valideeParNom, String motifRejet, Instant rouverteLe, String rouverteParNom,
                             String motifReouverture, long ecartTotal, List<LigneDto> lignes) {
    }

    /** Journee avec activite qui n'est pas encore cloturee. */
    public record AFaire(Long agenceId, String agenceNom, LocalDate journee, String statut) {
    }

    // ----------------------------------------------------------- preparation

    /** Ce que la cloture de la journee doit verifier, et la cloture deja faite s'il y en a une. */
    public Preparation preparer(Long agenceId, LocalDate journee) {
        Agence agence = perimetre.exiger(agenceId);
        LocalDate courante = registre.journee(Instant.now());
        LocalDate jour = journee != null ? journee : courante;
        List<CompteAControler> aControler = comptesAControler(agence, jour);
        List<String> ouvertes = sessions.findByAgenceIdAndStatut(agence.getId(), SessionCaisseAgence.Statut.OUVERTE).stream()
                .map(s -> s.getCaissierNom() + " (" + s.getNumero() + ")").toList();
        ClotureDto existante = repository.findByAgenceIdAndJournee(agence.getId(), jour).map(c -> versDto(c, agence.getNom())).orElse(null);
        return new Preparation(agence.getId(), agence.getNom(), jour, courante, ecritures.countByAgenceIdAndJournee(agence.getId(), jour),
                aControler, ouvertes, existante);
    }

    private List<CompteAControler> comptesAControler(Agence agence, LocalDate jour) {
        return comptes.synchroniser(agence).stream()
                .filter(CompteAgence::isActif)
                .sorted(Comparator.comparingInt((CompteAgence c) -> c.getType().getFamille().ordinal()).thenComparing(CompteAgence::getId))
                .map(c -> new CompteAControler(c.getId(), c.getLibelle(), c.getType().getFamille().name(), obligatoire(c),
                        lignes.soldeAu(c.getId(), jour)))
                .toList();
    }

    /** Le cash et chaque solde virtuel sont toujours controles ; une caisse de caissier fermee vaut zero. */
    private static boolean obligatoire(CompteAgence c) {
        return c.getType() == TypeCompte.CAISSE_GENERALE || c.getType().getFamille() == TypeCompte.Famille.VIRTUEL;
    }

    // -------------------------------------------------------------- soumission

    public ClotureDto soumettre(Soumission s, UtilisateurPrincipal auteur) {
        Agence agence = perimetre.exiger(s.agenceId());
        LocalDate courante = registre.journee(Instant.now());
        if (s.journee().isAfter(courante)) {
            throw new BusinessException("La journee du " + s.journee().format(JJ_MM_AAAA) + " n'a pas encore commence");
        }
        ClotureAgence c = repository.findByAgenceIdAndJournee(agence.getId(), s.journee()).orElseGet(ClotureAgence::new);
        if (c.getStatut() == ClotureAgence.Statut.SOUMISE) {
            throw new BusinessException("La cloture du " + s.journee().format(JJ_MM_AAAA) + " est deja soumise : elle attend sa validation");
        }
        if (c.getStatut() == ClotureAgence.Statut.VALIDEE) {
            throw new BusinessException("La journee du " + s.journee().format(JJ_MM_AAAA) + " est deja cloturee");
        }
        List<SessionCaisseAgence> ouvertes = sessions.findByAgenceIdAndStatut(agence.getId(), SessionCaisseAgence.Statut.OUVERTE);
        if (!ouvertes.isEmpty()) {
            throw new BusinessException("Fermez d'abord les caisses ouvertes : " + ouvertes.stream()
                    .map(SessionCaisseAgence::getCaissierNom).collect(Collectors.joining(", ")));
        }
        Map<Long, LigneSaisie> saisies = s.lignes().stream().collect(Collectors.toMap(LigneSaisie::compteId, Function.identity(), (a, b) -> b));
        List<LigneCloture> lignesCloture = new ArrayList<>();
        for (CompteAControler compte : comptesAControler(agence, s.journee())) {
            LigneSaisie saisie = saisies.get(compte.compteId());
            Long reel = saisie == null ? null : saisie.soldeReel();
            if (reel == null && compte.obligatoire()) {
                throw new BusinessException("Indiquez le solde reel de " + compte.libelle());
            }
            Long ecart = reel == null ? null : reel - compte.soldeTheorique();
            String justification = saisie == null || saisie.justification() == null || saisie.justification().isBlank()
                    ? null : saisie.justification().trim();
            if (ecart != null && ecart != 0 && justification == null) {
                throw new BusinessException("Justifiez l'ecart de " + RegistreService.francs(ecart) + " sur " + compte.libelle());
            }
            lignesCloture.add(new LigneCloture(compte.compteId(), compte.libelle(), compte.famille(), compte.soldeTheorique(),
                    reel, ecart, justification));
        }
        c.setAgenceId(agence.getId());
        c.setJournee(s.journee());
        c.setStatut(ClotureAgence.Statut.SOUMISE);
        c.setObservations(s.observations() == null || s.observations().isBlank() ? null : s.observations().trim());
        c.setSoumiseLe(Instant.now());
        c.setSoumiseParId(auteur.getId());
        c.setSoumiseParNom(auteur.getNomComplet());
        c.setMotifRejet(null);
        c.setValideeLe(null);
        c.setValideeParId(null);
        c.setValideeParNom(null);
        c.getLignes().clear();
        c.getLignes().addAll(lignesCloture);
        c = repository.save(c);
        ClotureDto dto = versDto(c, agence.getNom());
        audit.tracer(agence.getId(), "SOUMISSION_CLOTURE", "CLOTURE", c.getId(), "Cloture du " + s.journee().format(JJ_MM_AAAA)
                + " de " + agence.getNom() + " soumise (ecart total " + RegistreService.francs(dto.ecartTotal()) + ")", null, dto.lignes());
        return dto;
    }

    // ------------------------------------------------------------- decisions

    /** Valide la cloture : les soldes theoriques doivent etre restes ceux de la soumission. */
    public ClotureDto valider(Long id, UtilisateurPrincipal validateur) {
        ClotureAgence c = charger(id);
        Agence agence = perimetre.exiger(c.getAgenceId());
        if (c.getStatut() != ClotureAgence.Statut.SOUMISE) {
            throw new BusinessException("Seule une cloture soumise se valide");
        }
        if (Objects.equals(c.getSoumiseParId(), validateur.getId()) && !DroitsAgence.estAdministrateur()) {
            throw new AccessDeniedException("La cloture se valide par une autre personne que celle qui l'a soumise");
        }
        for (LigneCloture l : c.getLignes()) {
            long actuel = lignes.soldeAu(l.getCompteId(), c.getJournee());
            if (actuel != l.getSoldeTheorique()) {
                throw new BusinessException("Des operations ont eu lieu depuis la soumission (" + l.getLibelle() + " : "
                        + RegistreService.francs(l.getSoldeTheorique()) + " soumis, " + RegistreService.francs(actuel)
                        + " maintenant) : rejetez la cloture pour qu'elle soit refaite");
            }
        }
        c.setStatut(ClotureAgence.Statut.VALIDEE);
        c.setValideeLe(Instant.now());
        c.setValideeParId(validateur.getId());
        c.setValideeParNom(validateur.getNomComplet());
        audit.tracer(agence.getId(), "VALIDATION_CLOTURE", "CLOTURE", c.getId(), "Cloture du " + c.getJournee().format(JJ_MM_AAAA)
                + " de " + agence.getNom() + " validee : journee verrouillee", "SOUMISE", "VALIDEE");
        return versDto(c, agence.getNom());
    }

    public ClotureDto rejeter(Long id, String motif, UtilisateurPrincipal validateur) {
        ClotureAgence c = charger(id);
        Agence agence = perimetre.exiger(c.getAgenceId());
        if (c.getStatut() != ClotureAgence.Statut.SOUMISE) {
            throw new BusinessException("Seule une cloture soumise se rejette");
        }
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif du rejet est obligatoire");
        }
        c.setStatut(ClotureAgence.Statut.REJETEE);
        c.setMotifRejet(motif.trim());
        c.setValideeLe(Instant.now());
        c.setValideeParId(validateur.getId());
        c.setValideeParNom(validateur.getNomComplet());
        audit.tracer(agence.getId(), "REJET_CLOTURE", "CLOTURE", c.getId(), "Cloture du " + c.getJournee().format(JJ_MM_AAAA)
                + " rejetee (" + motif.trim() + ")", "SOUMISE", "REJETEE");
        return versDto(c, agence.getNom());
    }

    /** Autorisation explicite de l'administrateur : la journee recoit de nouveau des ecritures. */
    public ClotureDto rouvrir(Long id, String motif, UtilisateurPrincipal admin) {
        if (!DroitsAgence.estAdministrateur()) {
            throw new AccessDeniedException("Seul l'administrateur rouvre une journee cloturee");
        }
        ClotureAgence c = charger(id);
        Agence agence = perimetre.exiger(c.getAgenceId());
        if (c.getStatut() != ClotureAgence.Statut.VALIDEE) {
            throw new BusinessException("Seule une journee cloturee se rouvre");
        }
        if (motif == null || motif.isBlank()) {
            throw new BusinessException("Le motif de la reouverture est obligatoire");
        }
        c.setStatut(ClotureAgence.Statut.ROUVERTE);
        c.setRouverteLe(Instant.now());
        c.setRouverteParNom(admin.getNomComplet());
        c.setMotifReouverture(motif.trim());
        audit.tracer(agence.getId(), "REOUVERTURE_CLOTURE", "CLOTURE", c.getId(), "Journee du " + c.getJournee().format(JJ_MM_AAAA)
                + " de " + agence.getNom() + " rouverte (" + motif.trim() + ")", "VALIDEE", "ROUVERTE");
        return versDto(c, agence.getNom());
    }

    // --------------------------------------------------------------- lecture

    @Transactional(readOnly = true)
    public Page<ClotureDto> lister(Long agenceId, Pageable pageable) {
        Map<Long, String> noms = noms(agenceId);
        return repository.findByAgenceIdInOrderByJourneeDescIdDesc(noms.isEmpty() ? Set.of(-1L) : noms.keySet(), pageable)
                .map(c -> versDto(c, noms.get(c.getAgenceId())));
    }

    /** Journees des 60 derniers jours avec activite, avant la journee en cours, sans cloture validee. */
    @Transactional(readOnly = true)
    public List<AFaire> aFaire(Long agenceId) {
        Map<Long, String> noms = noms(agenceId);
        if (noms.isEmpty()) {
            return List.of();
        }
        LocalDate courante = registre.journee(Instant.now());
        LocalDate depuis = courante.minusDays(60);
        Map<String, ClotureAgence> clotures = repository.findByAgenceIdInAndJourneeGreaterThanEqual(noms.keySet(), depuis).stream()
                .collect(Collectors.toMap(c -> c.getAgenceId() + "|" + c.getJournee(), Function.identity()));
        List<AFaire> resultat = new ArrayList<>();
        for (Object[] r : ecritures.journeesActives(noms.keySet(), depuis, courante)) {
            Long agence = (Long) r[0];
            LocalDate jour = (LocalDate) r[1];
            ClotureAgence c = clotures.get(agence + "|" + jour);
            if (c == null || c.getStatut() != ClotureAgence.Statut.VALIDEE) {
                resultat.add(new AFaire(agence, noms.get(agence), jour, c == null ? "A FAIRE" : c.getStatut().name()));
            }
        }
        resultat.sort(Comparator.comparing(AFaire::journee).thenComparing(AFaire::agenceNom));
        return resultat;
    }

    private Map<Long, String> noms(Long agenceId) {
        List<Agence> agences = agenceId != null ? List.of(perimetre.exiger(agenceId)) : perimetre.visibles();
        return agences.stream().collect(Collectors.toMap(Agence::getId, Agence::getNom));
    }

    private ClotureAgence charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Cloture", id));
    }

    private static ClotureDto versDto(ClotureAgence c, String agenceNom) {
        List<LigneDto> l = c.getLignes().stream()
                .map(x -> new LigneDto(x.getCompteId(), x.getLibelle(), x.getFamille(), x.getSoldeTheorique(), x.getSoldeReel(),
                        x.getEcart(), x.getJustification()))
                .toList();
        long ecartTotal = l.stream().filter(x -> x.ecart() != null).mapToLong(LigneDto::ecart).sum();
        return new ClotureDto(c.getId(), c.getAgenceId(), agenceNom, c.getJournee(), c.getStatut(), c.getObservations(),
                c.getSoumiseLe(), c.getSoumiseParNom(), c.getValideeLe(), c.getValideeParNom(), c.getMotifRejet(),
                c.getRouverteLe(), c.getRouverteParNom(), c.getMotifReouverture(), ecartTotal, l);
    }
}
