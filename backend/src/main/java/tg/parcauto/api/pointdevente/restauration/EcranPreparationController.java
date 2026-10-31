package tg.parcauto.api.pointdevente.restauration;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import tg.parcauto.api.security.Autorisations;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.util.List;

/** Ecrans de preparation : creation, categories recues, personnel affecte. */
@RestController
@ModuleRequis(value = ModuleApplicatif.POINT_DE_VENTE, variante = "RESTAURATION")
@RequestMapping("/api/pdv/restauration/ecrans")
@RequiredArgsConstructor
@Tag(name = "Point de vente - ecrans de preparation")
public class EcranPreparationController {

    private final EcranPreparationService service;

    @GetMapping
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Ecrans de preparation, et categories preparees qu'aucun ecran ne recoit")
    public EcranPreparationService.Liste lister() {
        return service.lister();
    }

    @GetMapping("/accessibles")
    @PreAuthorize(Autorisations.PDV_PREPARATION)
    @Operation(summary = "Ecrans que l'utilisateur peut ouvrir")
    public List<EcranPreparationService.Reponse> accessibles(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.accessibles(principal);
    }

    @PostMapping
    @PreAuthorize(Autorisations.PDV_GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cree un ecran de preparation")
    public EcranPreparationService.Reponse creer(@Valid @RequestBody EcranPreparationService.Requete requete) {
        return service.creer(requete);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Modifie un ecran de preparation")
    public EcranPreparationService.Reponse modifier(@PathVariable Long id,
                                                    @Valid @RequestBody EcranPreparationService.Requete requete) {
        return service.modifier(id, requete);
    }
}
