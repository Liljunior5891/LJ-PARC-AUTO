package tg.parcauto.api.plateforme.entreprise;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleService;
import tg.parcauto.api.security.UtilisateurRepository;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Administration des entreprises abonnees.
 * <p>
 * Ne manipule que les tables de la plateforme (entreprises, comptes, modules) :
 * l'exploitant voit qui est abonne a quoi, jamais les donnees metier.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PlateformeService {

    private final EntrepriseRepository repository;
    private final UtilisateurRepository utilisateurRepository;
    private final ModuleService moduleService;
    private final tg.parcauto.api.plateforme.inscription.InscriptionService inscription;

    public record EntrepriseResume(
            Long id, String nom, String pays, String email, String telephone,
            StatutEntreprise statut, String statutLibelle, Instant creeLe,
            long nombreUtilisateurs, List<String> modules,
            /** Module Agences : plusieurs agences autorisees. */
            boolean agencesMultiples) {
    }

    public record Statistiques(long entreprises, long entreprisesActives, long utilisateurs,
                               Map<String, Long> activationsParModule) {
    }

    /**
     * Creation d'une entreprise par l'exploitant : meme traitement que l'inscription en
     * libre-service (entreprise, parametrage, administrateur, modules), avec un essai
     * dont la duree peut differer du reglage.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public EntrepriseResume creer(tg.parcauto.api.plateforme.inscription.InscriptionService.Demande demande,
                                  Integer joursEssai) {
        String email = inscription.inscrire(demande, joursEssai);
        Long id = utilisateurRepository.findByEmailIgnoreCase(email).orElseThrow().getEntrepriseId();
        return resume(repository.findById(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<EntrepriseResume> entreprises() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "creeLe")).stream()
                .map(this::resume)
                .toList();
    }

    @Transactional(readOnly = true)
    public Statistiques statistiques() {
        List<Entreprise> entreprises = repository.findAll();
        Map<ModuleApplicatif, Long> activations = entreprises.stream()
                .flatMap(e -> moduleService.actifs(e.getId()).stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        Map<String, Long> parModule = Arrays.stream(ModuleApplicatif.values())
                .collect(Collectors.toMap(Enum::name, m -> activations.getOrDefault(m, 0L),
                        (a, b) -> a, java.util.LinkedHashMap::new));
        return new Statistiques(
                entreprises.size(),
                entreprises.stream().filter(Entreprise::active).count(),
                utilisateurRepository.count(),
                parModule);
    }

    public EntrepriseResume changerStatut(Long id, StatutEntreprise statut) {
        Entreprise entreprise = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Entreprise", id));
        entreprise.setStatut(statut);
        return resume(entreprise);
    }

    /** Autorise ou retire la gestion de plusieurs agences ; les agences deja creees sont conservees. */
    public EntrepriseResume autoriserAgencesMultiples(Long id, boolean autorise) {
        Entreprise entreprise = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Entreprise", id));
        entreprise.setAgencesMultiples(autorise);
        return resume(entreprise);
    }

    private EntrepriseResume resume(Entreprise e) {
        return new EntrepriseResume(
                e.getId(), e.getNom(), e.getPays(), e.getEmail(), e.getTelephone(),
                e.getStatut(), e.getStatut().getLibelle(), e.getCreeLe(),
                utilisateurRepository.countByEntrepriseId(e.getId()),
                moduleService.actifs(e.getId()).stream().map(Enum::name).toList(),
                e.isAgencesMultiples());
    }
}
