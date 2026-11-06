package tg.parcauto.api.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Code PIN de l'utilisateur connecte : il deverrouille son ecran de caisse. */
@RestController
@RequestMapping("/api/auth/pin")
@RequiredArgsConstructor
@Tag(name = "Authentification - code PIN")
public class PinController {

    private final PinService service;

    public record Pin(@NotBlank @Pattern(regexp = "^\\d{4,6}$", message = "4 a 6 chiffres") String pin) {
    }

    public record Etat(boolean defini) {
    }

    @GetMapping
    @Operation(summary = "Indique si l'utilisateur a deja un code PIN")
    public Etat etat(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return new Etat(service.defini(principal.getId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cree son code PIN, s'il n'en a pas encore")
    public void creer(@Valid @RequestBody Pin pin, @AuthenticationPrincipal UtilisateurPrincipal principal) {
        service.creer(principal.getId(), pin.pin());
    }

    public record Changement(
            @NotBlank @Pattern(regexp = "^\\d{4,6}$", message = "4 a 6 chiffres") String actuel,
            @NotBlank @Pattern(regexp = "^\\d{4,6}$", message = "4 a 6 chiffres") String nouveau) {
    }

    @PostMapping("/changement")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change son code PIN en donnant le code actuel")
    public void changer(@Valid @RequestBody Changement changement, @AuthenticationPrincipal UtilisateurPrincipal principal) {
        service.changer(principal.getId(), changement.actuel(), changement.nouveau());
    }

    @PostMapping("/verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Verifie le code PIN pour deverrouiller l'ecran de caisse")
    public void verifier(@Valid @RequestBody Pin pin, @AuthenticationPrincipal UtilisateurPrincipal principal) {
        service.verifier(principal.getId(), pin.pin());
    }
}
