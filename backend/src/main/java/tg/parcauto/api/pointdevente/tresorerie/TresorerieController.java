package tg.parcauto.api.pointdevente.tresorerie;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.Autorisations;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.util.List;

/**
 * Circuit des especes (option des reglages) : remise du caissier, validation et
 * coffre du gerant, degagement vers l'administrateur, compte de l'entreprise.
 */
@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@RequestMapping("/api/pdv/tresorerie")
@RequiredArgsConstructor
@Tag(name = "Point de vente - remises, coffre et compte de l'entreprise")
public class TresorerieController {

    private final TresorerieService service;

    // ------------------------------------------------------------ caissier

    @GetMapping("/sessions/{id}")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Somme a remettre et remises de la session")
    public TresorerieService.EtatSession etatSession(@PathVariable Long id,
                                                     @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.etatSession(id, principal);
    }

    @PostMapping("/sessions/{id}/remises")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Envoie au gerant le billettage de la recette (fin de service)")
    public TresorerieService.RemiseDto envoyerRemise(@PathVariable Long id, @Valid @RequestBody TresorerieService.Envoi envoi,
                                                     @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.envoyerRemise(id, envoi, principal);
    }

    // -------------------------------------------------------------- gerant

    @GetMapping("/en-attente")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Nombre de remises et de degagements a valider")
    public TresorerieService.EnAttente enAttente() {
        return service.enAttente();
    }

    @GetMapping("/remises/en-attente")
    @PreAuthorize(Autorisations.PDV_GESTION)
    public List<TresorerieService.RemiseDto> remisesEnAttente() {
        return service.remisesEnAttente();
    }

    @GetMapping("/remises")
    @PreAuthorize(Autorisations.PDV_GESTION)
    public Page<TresorerieService.RemiseDto> historiqueRemises(@RequestParam(defaultValue = "0") int page) {
        return service.historiqueRemises(page);
    }

    @PostMapping("/remises/{id}/validation")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Valide une remise apres recompte : l'argent passe du tiroir au coffre")
    public TresorerieService.RemiseDto validerRemise(@PathVariable Long id,
                                                     @Valid @RequestBody TresorerieService.Validation validation,
                                                     @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.validerRemise(id, validation, principal);
    }

    @PostMapping("/remises/{id}/refus")
    @PreAuthorize(Autorisations.PDV_GESTION)
    public TresorerieService.RemiseDto refuserRemise(@PathVariable Long id, @Valid @RequestBody TresorerieService.Refus refus,
                                                     @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.refuserRemise(id, refus.motif(), principal);
    }

    @GetMapping("/coffre")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Coffre du gerant connecte : solde, mouvements, degagement en cours")
    public TresorerieService.Coffre coffre(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.coffre(principal);
    }

    @PostMapping("/degagements")
    @PreAuthorize("hasRole('GERANT')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Degage le coffre vers l'administrateur, avec billettage")
    public TresorerieService.DegagementDto envoyerDegagement(@Valid @RequestBody TresorerieService.Envoi envoi,
                                                             @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.envoyerDegagement(envoi, principal);
    }

    // ------------------------------------------------------ administrateur

    @GetMapping("/degagements/en-attente")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    public List<TresorerieService.DegagementDto> degagementsEnAttente() {
        return service.degagementsEnAttente();
    }

    @GetMapping("/degagements")
    @PreAuthorize(Autorisations.PDV_GESTION)
    public Page<TresorerieService.DegagementDto> historiqueDegagements(@RequestParam(defaultValue = "0") int page) {
        return service.historiqueDegagements(page);
    }

    @PostMapping("/degagements/{id}/validation")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Valide un degagement apres recompte : l'argent arrive sur le compte de l'entreprise")
    public TresorerieService.DegagementDto validerDegagement(@PathVariable Long id,
                                                             @Valid @RequestBody TresorerieService.Validation validation,
                                                             @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.validerDegagement(id, validation, principal);
    }

    @PostMapping("/degagements/{id}/refus")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    public TresorerieService.DegagementDto refuserDegagement(@PathVariable Long id,
                                                             @Valid @RequestBody TresorerieService.Refus refus,
                                                             @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.refuserDegagement(id, refus.motif(), principal);
    }

    @GetMapping("/compte-entreprise")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Compte de l'entreprise : entrees et soldes des coffres")
    public TresorerieService.CompteEntreprise compteEntreprise() {
        return service.compteEntreprise();
    }
}
