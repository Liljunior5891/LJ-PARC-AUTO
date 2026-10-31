package tg.parcauto.api.pointdevente.caisse;

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

/** Caisses du point de vente : creation, affectation au personnel, etat. */
@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@RequestMapping("/api/pdv/caisses")
@RequiredArgsConstructor
@Tag(name = "Point de vente - caisses")
public class GestionCaisseController {

    private final GestionCaisseService service;

    @GetMapping
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Caisses de l'etablissement, leurs affectations et qui les tient")
    public List<GestionCaisseService.Reponse> lister() {
        return service.lister();
    }

    @GetMapping("/disponibles")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Caisses que l'utilisateur peut ouvrir")
    public List<GestionCaisseService.Reponse> disponibles(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.disponibles(principal);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Une caisse, ses affectations et sa session en cours")
    public GestionCaisseService.Reponse detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @PreAuthorize(Autorisations.PDV_GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cree une caisse et l'affecte a des utilisateurs")
    public GestionCaisseService.Reponse creer(@Valid @RequestBody GestionCaisseService.Requete requete) {
        return service.creer(requete);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Modifie une caisse et ses affectations")
    public GestionCaisseService.Reponse modifier(@PathVariable Long id,
                                                 @Valid @RequestBody GestionCaisseService.Requete requete) {
        return service.modifier(id, requete);
    }
}
