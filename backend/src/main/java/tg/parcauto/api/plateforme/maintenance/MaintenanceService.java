package tg.parcauto.api.plateforme.maintenance;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Maintenances programmees par l'exploitant. La liste des maintenances a venir
 * ou en cours est gardee en memoire quelques secondes : elle est consultee a
 * chaque appel d'un ecran de module.
 */
@Service
@RequiredArgsConstructor
public class MaintenanceService {

    /** Duree de vie de la copie en memoire ; une modification la vide aussitot. */
    private static final Duration FRAICHEUR = Duration.ofSeconds(15);
    /** Annonce par defaut : la veille. */
    private static final Duration ANNONCE_PAR_DEFAUT = Duration.ofHours(24);
    private static final Duration DUREE_MAX = Duration.ofDays(7);

    private final MaintenanceRepository repository;
    private final AtomicReference<Copie> copie = new AtomicReference<>();
    /** Augmente a chaque modification : une lecture commencee avant ne remet pas son resultat en memoire. */
    private final java.util.concurrent.atomic.AtomicLong generation = new java.util.concurrent.atomic.AtomicLong();

    private record Copie(Instant lueLe, List<Maintenance> liste) {
    }

    public record ModuleVue(String code, String libelle) {
        static ModuleVue de(ModuleApplicatif m) {
            return new ModuleVue(m.name(), m.getLibelle());
        }
    }

    public record MaintenanceVue(Long id, String message, List<ModuleVue> modules, Instant annonceLe, Instant debut,
                                 Instant finPrevue, Instant termineeLe, Instant annuleeLe, Maintenance.Statut statut,
                                 String creePar, Instant creeLe) {
        static MaintenanceVue de(Maintenance m, Instant maintenant) {
            return new MaintenanceVue(m.getId(), m.getMessage(),
                    m.getModules().stream().sorted().map(ModuleVue::de).toList(),
                    m.getAnnonceLe(), m.getDebut(), m.getFinPrevue(), m.getTermineeLe(), m.getAnnuleeLe(),
                    m.statut(maintenant), m.getCreePar(), m.getCreeLe());
        }
    }

    /**
     * Programmation d'une maintenance. Sans {@code annonceHeures}, elle est
     * annoncee 24 heures avant ; 0 = sans annonce prealable.
     */
    public record Saisie(@NotBlank @Size(max = 500) String message,
                         @NotEmpty List<@NotNull ModuleApplicatif> modules,
                         @NotNull Instant debut,
                         @NotNull Instant finPrevue,
                         @PositiveOrZero @Max(720) Integer annonceHeures) {
    }

    // ------------------------------------------------------------- lecture

    /** Maintenance en cours sur ce module, s'il y en a une (celle qui finit le plus tard). */
    public Optional<Maintenance> enCours(ModuleApplicatif module) {
        Instant maintenant = Instant.now();
        return aVenirOuEnCours().stream()
                .filter(m -> m.getModules().contains(module) && m.statut(maintenant) == Maintenance.Statut.EN_COURS)
                .max(Comparator.comparing(Maintenance::finEffective));
    }

    /** Maintenances annoncees ou en cours, pour le bandeau et la page de maintenance des utilisateurs. */
    public List<MaintenanceVue> annonces() {
        Instant maintenant = Instant.now();
        return aVenirOuEnCours().stream()
                .filter(m -> {
                    Maintenance.Statut s = m.statut(maintenant);
                    return s == Maintenance.Statut.ANNONCEE || s == Maintenance.Statut.EN_COURS;
                })
                .map(m -> MaintenanceVue.de(m, maintenant)).toList();
    }

    /** Console : les 100 maintenances les plus recentes. */
    @Transactional(readOnly = true)
    public List<MaintenanceVue> liste() {
        Instant maintenant = Instant.now();
        return repository.findTop100ByOrderByDebutDesc().stream().map(m -> MaintenanceVue.de(m, maintenant)).toList();
    }

    // ------------------------------------------------------------- ecriture

    @Transactional
    public MaintenanceVue programmer(Saisie s) {
        Maintenance m = new Maintenance();
        appliquer(m, s);
        return vue(repository.save(m));
    }

    /** Modifie une maintenance pas encore finie : prolonger, avancer, changer le message ou les modules. */
    @Transactional
    public MaintenanceVue modifier(Long id, Saisie s) {
        Maintenance m = trouver(id);
        Instant maintenant = Instant.now();
        Maintenance.Statut statut = m.statut(maintenant);
        if (statut == Maintenance.Statut.TERMINEE || statut == Maintenance.Statut.ANNULEE) {
            throw new BusinessException("Cette maintenance est "
                    + (statut == Maintenance.Statut.ANNULEE ? "annulee" : "terminee") + " : programmez-en une nouvelle");
        }
        if (statut == Maintenance.Statut.EN_COURS && s.debut().isAfter(maintenant)) {
            throw new BusinessException("La maintenance a deja commence : son debut ne peut plus etre repousse, terminez-la");
        }
        appliquer(m, s);
        return vue(m);
    }

    /** Fin anticipee d'une maintenance en cours : les ecrans rouvrent aussitot. */
    @Transactional
    public MaintenanceVue terminer(Long id) {
        Maintenance m = trouver(id);
        if (m.statut(Instant.now()) != Maintenance.Statut.EN_COURS) {
            throw new BusinessException("Seule une maintenance en cours peut etre terminee ; une maintenance a venir s'annule");
        }
        m.setTermineeLe(Instant.now());
        return vue(m);
    }

    /** Annulation d'une maintenance qui n'a pas commence. */
    @Transactional
    public MaintenanceVue annuler(Long id) {
        Maintenance m = trouver(id);
        Maintenance.Statut statut = m.statut(Instant.now());
        if (statut != Maintenance.Statut.PROGRAMMEE && statut != Maintenance.Statut.ANNONCEE) {
            throw new BusinessException("Seule une maintenance a venir peut etre annulee ; une maintenance en cours se termine");
        }
        m.setAnnuleeLe(Instant.now());
        return vue(m);
    }

    // ------------------------------------------------------------- internes

    private void appliquer(Maintenance m, Saisie s) {
        if (!s.finPrevue().isAfter(s.debut())) {
            throw new BusinessException("La fin prevue doit suivre le debut de la maintenance");
        }
        if (!s.finPrevue().isAfter(Instant.now())) {
            throw new BusinessException("La fin prevue est deja passee");
        }
        if (Duration.between(s.debut(), s.finPrevue()).compareTo(DUREE_MAX) > 0) {
            throw new BusinessException("Une maintenance ne peut pas durer plus de 7 jours");
        }
        Duration avance = s.annonceHeures() == null ? ANNONCE_PAR_DEFAUT : Duration.ofHours(s.annonceHeures());
        m.setMessage(s.message().trim());
        m.setModules(EnumSet.copyOf(s.modules()));
        m.setDebut(s.debut());
        m.setFinPrevue(s.finPrevue());
        m.setAnnonceLe(s.debut().minus(avance));
    }

    private Maintenance trouver(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException("Maintenance introuvable"));
    }

    /**
     * Vide la copie en memoire, et encore une fois apres la validation. La generation
     * change a chaque fois : une lecture lancee avant ne peut plus y remettre l'ancien etat.
     */
    private MaintenanceVue vue(Maintenance m) {
        invalider();
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            invalider();
                        }
                    });
        }
        return MaintenanceVue.de(m, Instant.now());
    }

    private void invalider() {
        generation.incrementAndGet();
        copie.set(null);
    }

    private List<Maintenance> aVenirOuEnCours() {
        Instant maintenant = Instant.now();
        Copie c = copie.get();
        if (c == null || c.lueLe().plus(FRAICHEUR).isBefore(maintenant)) {
            long avant = generation.get();
            Copie lue = new Copie(maintenant, repository.aVenirOuEnCours(maintenant));
            // Une modification validee pendant la lecture : le resultat sert a cet appel, pas aux suivants.
            if (generation.get() == avant && copie.compareAndSet(c, lue) && generation.get() != avant) {
                // Modifiee entre le controle et l'enregistrement : on retire ce resultat perime.
                copie.compareAndSet(lue, null);
            }
            return lue.liste();
        }
        return c.liste();
    }
}
