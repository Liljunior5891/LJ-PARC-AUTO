package tg.parcauto.api.plateforme.module;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Activation des modules par les entreprises. */
@Service
@RequiredArgsConstructor
@Transactional
public class ModuleService {

    private final EntrepriseModuleRepository repository;
    private final List<GardeVariante> gardes;

    public record ModuleCatalogue(String code, String libelle, String description,
                                  boolean disponible, boolean actif,
                                  /** Variante retenue par l'entreprise, conservee meme module desactive. */
                                  String variante,
                                  List<ModuleApplicatif.Variante> variantes) {
    }

    @Transactional(readOnly = true)
    public Set<ModuleApplicatif> actifs(Long entrepriseId) {
        if (entrepriseId == null) {
            return EnumSet.noneOf(ModuleApplicatif.class);
        }
        return repository.findByEntrepriseIdAndActifTrue(entrepriseId).stream()
                .map(EntrepriseModule::getModule)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ModuleApplicatif.class)));
    }

    /** Variante de chaque module actif qui en a une. */
    @Transactional(readOnly = true)
    public Map<ModuleApplicatif, String> variantes(Long entrepriseId) {
        Map<ModuleApplicatif, String> variantes = new EnumMap<>(ModuleApplicatif.class);
        if (entrepriseId == null) {
            return variantes;
        }
        for (EntrepriseModule souscription : repository.findByEntrepriseIdAndActifTrue(entrepriseId)) {
            String variante = varianteEffective(souscription);
            if (variante != null) {
                variantes.put(souscription.getModule(), variante);
            }
        }
        return variantes;
    }

    @Transactional(readOnly = true)
    public boolean estActif(Long entrepriseId, ModuleApplicatif module) {
        return entrepriseId != null && repository.existsByEntrepriseIdAndModuleAndActifTrue(entrepriseId, module);
    }

    /** Variante du module pour l'entreprise ; nulle si le module n'est pas actif. */
    @Transactional(readOnly = true)
    public String variante(Long entrepriseId, ModuleApplicatif module) {
        if (entrepriseId == null) {
            return null;
        }
        return repository.findByEntrepriseIdAndModule(entrepriseId, module)
                .filter(EntrepriseModule::isActif)
                .map(this::varianteEffective)
                .orElse(null);
    }

    /** Catalogue complet ; entreprise nulle pour un visiteur, qui n'a rien d'active. */
    @Transactional(readOnly = true)
    public List<ModuleCatalogue> catalogue(Long entrepriseId) {
        Map<ModuleApplicatif, EntrepriseModule> souscriptions = new EnumMap<>(ModuleApplicatif.class);
        if (entrepriseId != null) {
            repository.findByEntrepriseId(entrepriseId).forEach(s -> souscriptions.put(s.getModule(), s));
        }
        return Arrays.stream(ModuleApplicatif.values())
                .map(m -> {
                    EntrepriseModule s = souscriptions.get(m);
                    return new ModuleCatalogue(m.name(), m.getLibelle(), m.getDescription(),
                            m.isDisponible(), s != null && s.isActif(),
                            s == null ? null : varianteEffective(s), m.getVariantes());
                })
                .toList();
    }

    public void activer(Long entrepriseId, ModuleApplicatif module) {
        activer(entrepriseId, module, null);
    }

    /**
     * Active le module. Variante nulle : celle deja choisie lors d'une activation
     * precedente, a defaut celle par defaut du module.
     */
    public void activer(Long entrepriseId, ModuleApplicatif module, String variante) {
        if (!module.isDisponible()) {
            throw new BusinessException("Le module " + module.getLibelle() + " n'est pas encore disponible");
        }
        EntrepriseModule souscription = repository.findByEntrepriseIdAndModule(entrepriseId, module)
                .orElseGet(() -> {
                    EntrepriseModule nouveau = new EntrepriseModule();
                    nouveau.setEntrepriseId(entrepriseId);
                    nouveau.setModule(module);
                    return nouveau;
                });
        if (variante != null && !variante.isBlank()) {
            String nouvelle = module.varianteValide(variante);
            if (souscription.getId() != null && !nouvelle.equals(varianteEffective(souscription))) {
                verifierChangement(module, varianteEffective(souscription), nouvelle);
            }
            souscription.setVariante(nouvelle);
        } else if (souscription.getVariante() == null) {
            souscription.setVariante(module.varianteParDefaut().orElse(null));
        }
        if (souscription.getId() == null || !souscription.isActif()) {
            souscription.setActif(true);
            souscription.setActiveLe(Instant.now());
        }
        repository.save(souscription);
    }

    /** Change le type d'activite d'un module actif, si rien n'est en cours dans l'ancien. */
    public void changerVariante(Long entrepriseId, ModuleApplicatif module, String variante) {
        EntrepriseModule souscription = repository.findByEntrepriseIdAndModule(entrepriseId, module)
                .filter(EntrepriseModule::isActif)
                .orElseThrow(() -> new BusinessException("Activez d'abord le module " + module.getLibelle()));
        if (module.getVariantes().isEmpty()) {
            throw new BusinessException("Le module " + module.getLibelle() + " n'a pas de type d'activite");
        }
        String nouvelle = module.varianteValide(variante);
        String actuelle = varianteEffective(souscription);
        if (nouvelle.equals(actuelle)) {
            return;
        }
        verifierChangement(module, actuelle, nouvelle);
        souscription.setVariante(nouvelle);
    }

    /** Les donnees du module sont conservees : une reactivation les retrouve intactes. */
    public void desactiver(Long entrepriseId, ModuleApplicatif module) {
        repository.findByEntrepriseIdAndModule(entrepriseId, module)
                .ifPresent(souscription -> souscription.setActif(false));
    }

    private void verifierChangement(ModuleApplicatif module, String actuelle, String nouvelle) {
        gardes.stream()
                .filter(garde -> garde.module() == module)
                .forEach(garde -> garde.verifierChangement(actuelle, nouvelle));
    }

    /**
     * Une souscription anterieure aux variantes prend la variante par defaut ;
     * un module sans variante (gestion de parc) n'en a aucune : null.
     */
    private String varianteEffective(EntrepriseModule souscription) {
        return souscription.getVariante() != null
                ? souscription.getVariante()
                : souscription.getModule().varianteParDefaut().orElse(null);
    }
}
