package tg.parcauto.api.pointdevente.horsligne;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.pointdevente.vente.VenteDto.VenteHorsLigne;
import tg.parcauto.api.security.Autorisations;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.util.List;
import java.util.Map;

@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@RequestMapping("/api/pdv/hors-ligne")
@RequiredArgsConstructor
@Validated
@Tag(name = "Point de vente - ventes hors ligne")
public class HorsLignePdvController {

    private final HorsLignePdvService service;

    @PostMapping("/synchronisation")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Envoie les ventes faites sans connexion ; chacune revient enregistree ou en conflit")
    public List<HorsLignePdvService.Resultat> synchroniser(
            @RequestBody @NotEmpty List<VenteHorsLigne> ventes,
            @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.synchroniser(ventes, principal);
    }

    @GetMapping
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Ventes hors ligne des 30 derniers jours et conflits ouverts")
    public List<HorsLignePdvService.VenteDto2> recentes() {
        return service.recentes();
    }

    @GetMapping("/conflits/nombre")
    @PreAuthorize(Autorisations.PDV_GESTION)
    public Map<String, Long> nombre() {
        return Map.of("conflits", service.nombreConflits());
    }

    @PostMapping("/{id}/reprise")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Enregistre une vente en conflit sur une caisse ouverte")
    public HorsLignePdvService.VenteDto2 reprendre(@PathVariable Long id,
                                                   @RequestBody(required = false) HorsLignePdvService.Reprise reprise,
                                                   @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.reprendre(id, reprise, principal);
    }

    @PostMapping("/{id}/abandon")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Abandonne une vente en conflit (reglee hors du logiciel), avec une note")
    public HorsLignePdvService.VenteDto2 abandonner(@PathVariable Long id,
                                                    @Valid @RequestBody HorsLignePdvService.Abandon abandon,
                                                    @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.abandonner(id, abandon, principal);
    }
}
