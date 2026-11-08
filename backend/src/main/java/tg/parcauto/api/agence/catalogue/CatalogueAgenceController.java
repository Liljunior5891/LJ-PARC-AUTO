package tg.parcauto.api.agence.catalogue;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;

import java.util.List;

/** Services, reseaux et types d'operation proposes au guichet des agences. */
@RestController
@ModuleRequis(ModuleApplicatif.AGENCES)
@RequestMapping("/api/agences/catalogue")
@RequiredArgsConstructor
@Tag(name = "Agences - services et operations")
public class CatalogueAgenceController {

    private final CatalogueAgenceService service;

    @GetMapping
    @Operation(summary = "Services, reseaux et types d'operation avec leur regle de mouvement")
    public List<CatalogueAgenceService.ServiceDto> catalogue() {
        return service.catalogue();
    }

    @GetMapping("/categories")
    @Operation(summary = "Categories de services deja employees")
    public List<String> categories() {
        return service.categories();
    }

    @PostMapping("/services")
    @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajoute un service")
    public CatalogueAgenceService.ServiceDto creerService(@Valid @RequestBody CatalogueAgenceService.RequeteService r) {
        return service.creerService(r);
    }

    @PutMapping("/services/{id}")
    @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")
    @Operation(summary = "Modifie, active ou desactive un service")
    public CatalogueAgenceService.ServiceDto modifierService(@PathVariable Long id,
                                                       @Valid @RequestBody CatalogueAgenceService.RequeteService r) {
        return service.modifierService(id, r);
    }

    @PostMapping("/reseaux")
    @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajoute un reseau a un service")
    public CatalogueAgenceService.ServiceDto creerReseau(@Valid @RequestBody CatalogueAgenceService.RequeteReseau r) {
        return service.creerReseau(r);
    }

    @PutMapping("/reseaux/{id}")
    @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")
    @Operation(summary = "Modifie, active ou desactive un reseau")
    public CatalogueAgenceService.ServiceDto modifierReseau(@PathVariable Long id,
                                                      @Valid @RequestBody CatalogueAgenceService.RequeteReseau r) {
        return service.modifierReseau(id, r);
    }

    @PostMapping("/types")
    @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajoute un type d'operation et sa regle de mouvement")
    public CatalogueAgenceService.ServiceDto creerType(@Valid @RequestBody CatalogueAgenceService.RequeteType r) {
        return service.creerType(r);
    }

    @PutMapping("/types/{id}")
    @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")
    @Operation(summary = "Modifie un type d'operation et sa regle de mouvement")
    public CatalogueAgenceService.ServiceDto modifierType(@PathVariable Long id,
                                                    @Valid @RequestBody CatalogueAgenceService.RequeteType r) {
        return service.modifierType(id, r);
    }
}
