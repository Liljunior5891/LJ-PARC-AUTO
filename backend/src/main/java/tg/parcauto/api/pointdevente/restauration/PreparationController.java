package tg.parcauto.api.pointdevente.restauration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.pointdevente.catalogue.PostePreparation;
import tg.parcauto.api.security.Autorisations;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.util.List;

/** Ecrans de preparation de la cuisine et du bar. */
@RestController
@ModuleRequis(value = ModuleApplicatif.POINT_DE_VENTE, variante = "RESTAURATION")
@RequestMapping("/api/pdv/restauration/preparation")
@RequiredArgsConstructor
@Tag(name = "Point de vente - cuisine et bar")
public class PreparationController {

    private final PreparationService service;

    @GetMapping
    @PreAuthorize(Autorisations.PDV_PREPARATION)
    @Operation(summary = "Bons a preparer, en preparation et prets depuis peu, pour la cuisine ou le bar")
    public List<RestaurationDto.Bon> bons(@RequestParam(required = false) Long ecran,
                                          @RequestParam(required = false) PostePreparation poste,
                                          @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return ecran != null ? service.bonsEcran(ecran, principal) : service.bons(poste);
    }

    @PostMapping("/bons/avancement")
    @PreAuthorize(Autorisations.PDV_PREPARATION)
    @Operation(summary = "Fait avancer un bon : en preparation, puis pret")
    public RestaurationDto.Bon avancerBon(@Valid @RequestBody RestaurationDto.AvancementBon demande,
                                          @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.avancerBon(demande, principal);
    }

    @PostMapping("/lignes/{id}/avancement")
    @PreAuthorize(Autorisations.PDV_PREPARATION)
    @Operation(summary = "Fait avancer une seule ligne d'un bon")
    public RestaurationDto.LigneBon avancerLigne(@PathVariable Long id) {
        return service.avancerLigne(id);
    }

    @PostMapping("/lignes/{id}/retour")
    @PreAuthorize(Autorisations.PDV_PREPARATION)
    @Operation(summary = "Ramene une ligne a l'etape precedente")
    public RestaurationDto.LigneBon reculerLigne(@PathVariable Long id) {
        return service.reculerLigne(id);
    }
}
