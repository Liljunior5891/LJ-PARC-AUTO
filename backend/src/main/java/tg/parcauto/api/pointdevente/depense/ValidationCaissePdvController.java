package tg.parcauto.api.pointdevente.depense;

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
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.pointdevente.caisse.SortieAttenteService;
import tg.parcauto.api.security.Autorisations;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.util.List;

/**
 * Validation des depenses et sorties payees par la caisse : visa du gerant,
 * puis approbation de l'administrateur qui fait sortir les especes.
 */
@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@RequestMapping("/api/pdv/validations-caisse")
@PreAuthorize(Autorisations.PDV_GESTION)
@RequiredArgsConstructor
@Tag(name = "Point de vente - validation des depenses de caisse")
public class ValidationCaissePdvController {

    private final DepensePdvService depenses;
    private final SortieAttenteService sorties;

    public record AValider(List<DepensePdvService.Reponse> depenses, List<SortieAttenteService.Reponse> sorties,
                           List<SortieAttenteService.Reponse> sortiesTraitees) {
    }

    @GetMapping
    @Operation(summary = "Depenses et sorties de caisse qui attendent ma validation, et les dernieres sorties traitees")
    public AValider aValider(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return new AValider(depenses.aValider(principal), sorties.aValider(principal), sorties.traitees(30));
    }

    @PostMapping("/depenses/{id}/validation")
    @Operation(summary = "Vise (gerant) ou approuve (administrateur) une depense de caisse")
    public DepensePdvService.Reponse validerDepense(@PathVariable Long id,
                                                    @org.springframework.web.bind.annotation.RequestParam(required = false) Integer version,
                                                    @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return depenses.valider(id, principal, version);
    }

    @PostMapping("/depenses/{id}/refus")
    @Operation(summary = "Refuse une depense de caisse en attente")
    public DepensePdvService.Reponse refuserDepense(@PathVariable Long id,
                                                    @Valid @RequestBody DepensePdvService.MotifRefus motif,
                                                    @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return depenses.refuser(id, motif.motif(), principal);
    }

    @PostMapping("/sorties/{id}/validation")
    @Operation(summary = "Vise (gerant) ou approuve (administrateur) une sortie de caisse demandee")
    public SortieAttenteService.Reponse validerSortie(@PathVariable Long id,
                                                      @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return sorties.valider(id, principal);
    }

    @PostMapping("/sorties/{id}/refus")
    @Operation(summary = "Refuse une sortie de caisse demandee")
    public SortieAttenteService.Reponse refuserSortie(@PathVariable Long id,
                                                      @Valid @RequestBody DepensePdvService.MotifRefus motif,
                                                      @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return sorties.refuser(id, motif.motif(), principal);
    }
}
