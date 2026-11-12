package tg.parcauto.api.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Double authentification du compte connecte : etat, activation, desactivation. */
@RestController
@RequestMapping("/api/auth/deux-facteurs")
@RequiredArgsConstructor
@Tag(name = "Authentification - double facteur")
public class DeuxFacteursController {

    private final DeuxFacteursService service;

    public record Code(@NotBlank @Size(max = 20) String code) {
    }

    public record Desactivation(@NotBlank @Size(max = 72) String motDePasse, @NotBlank @Size(max = 20) String code) {
    }

    @GetMapping
    @Operation(summary = "Etat de la double authentification du compte")
    public DeuxFacteursService.Etat etat(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.etat(principal.getId());
    }

    @PostMapping("/preparation")
    @Operation(summary = "Nouveau secret et son code QR, a scanner dans l'application d'authentification")
    public DeuxFacteursService.Preparation preparer(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.preparer(principal.getId());
    }

    @PostMapping("/activation")
    @Operation(summary = "Active la double authentification avec un premier code ; renvoie les codes de secours")
    public DeuxFacteursService.Activation activer(@AuthenticationPrincipal UtilisateurPrincipal principal,
                                                  @Valid @RequestBody Code code) {
        return service.activer(principal.getId(), code.code());
    }

    @PostMapping("/desactivation")
    @Operation(summary = "Desactive la double authentification (mot de passe et code exiges)")
    public void desactiver(@AuthenticationPrincipal UtilisateurPrincipal principal,
                           @Valid @RequestBody Desactivation demande) {
        service.desactiver(principal.getId(), demande.motDePasse(), demande.code());
    }
}
