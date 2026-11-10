package tg.parcauto.api.plateforme.module;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;
import tg.parcauto.api.security.Autorisations;

import java.util.List;

/**
 * Modules de l'entreprise connectee. Tout compte consulte le catalogue ;
 * seul l'administrateur de l'entreprise active ou desactive un module.
 */
@RestController
@RequestMapping("/api/modules")
@RequiredArgsConstructor
@Tag(name = "Modules")
public class ModuleController {

    private final tg.parcauto.api.plateforme.abonnement.AbonnementService abonnement;

    private final ModuleService service;

    /** Type d'activite choisi pour un module qui se decline (boutique ou restaurant). */
    public record ChoixVariante(String variante) {
    }

    @GetMapping
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Catalogue des modules et etat d'activation pour l'entreprise (administrateur)")
    public List<ModuleService.ModuleCatalogue> catalogue() {
        return service.catalogue(ContexteEntreprise.exiger());
    }

    @PostMapping("/{module}/activation")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Active un module pour l'entreprise, avec son type d'activite s'il en a un")
    public List<ModuleService.ModuleCatalogue> activer(@PathVariable ModuleApplicatif module,
                                                       @RequestBody(required = false) ChoixVariante choix) {
        Long entreprise = ContexteEntreprise.exiger();
        abonnement.verifierModuleAutorise(entreprise, module);
        service.activer(entreprise, module, choix == null ? null : choix.variante());
        return service.catalogue(entreprise);
    }

    @PutMapping("/{module}/variante")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Change le type d'activite d'un module actif")
    public List<ModuleService.ModuleCatalogue> changerVariante(@PathVariable ModuleApplicatif module,
                                                               @RequestBody ChoixVariante choix) {
        Long entreprise = ContexteEntreprise.exiger();
        service.changerVariante(entreprise, module, choix.variante());
        return service.catalogue(entreprise);
    }

    @DeleteMapping("/{module}/activation")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Desactive un module ; ses donnees sont conservees")
    public List<ModuleService.ModuleCatalogue> desactiver(@PathVariable ModuleApplicatif module) {
        Long entreprise = ContexteEntreprise.exiger();
        service.desactiver(entreprise, module);
        return service.catalogue(entreprise);
    }
}
