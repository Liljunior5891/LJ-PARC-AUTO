package tg.parcauto.api.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SessionService sessionService;
    private final tg.parcauto.api.common.audit.JournalActiviteService journal;
    private final UtilisateurRepository utilisateurs;
    private final PinService pinService;

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String motDePasse) {
    }

    /**
     * Profil connecte. Les modules actives de l'entreprise y figurent pour que
     * l'interface n'affiche que les espaces auxquels l'entreprise a souscrit.
     */
    public record UtilisateurCourant(
            Long id, String email, String nomComplet, Long chauffeurId, List<String> roles,
            Long entrepriseId, String entrepriseNom, List<String> modules,
            /** Type d'activite des modules qui se declinent, par code de module. */
            Map<String, String> variantes,
            /** Mot de passe provisoire : l'interface ouvre « Mon compte » pour le changer. */
            boolean motDePasseAChanger) {
    }

    public record LoginResponse(String token, long expiresIn, UtilisateurCourant utilisateur) {
    }

    @PostMapping("/login")
    @Operation(summary = "Authentifie un utilisateur et retourne un jeton JWT")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.motDePasse()));
        } catch (org.springframework.security.core.AuthenticationException e) {
            // Journal d'audit de l'entreprise du compte vise, s'il existe.
            // Compte du super-admin : journal general de la plateforme (entreprise vide).
            utilisateurs.findByEmailIgnoreCase(request.email())
                    .ifPresent(u -> journal.echecConnexion(u.getEntrepriseId(), u.getId(), u.nomComplet(),
                            e instanceof org.springframework.security.authentication.DisabledException ? "compte desactive" : "mot de passe incorrect"));
            throw e;
        }
        UtilisateurPrincipal principal = (UtilisateurPrincipal) authentication.getPrincipal();
        // Le mot de passe prouve l'identite : le PIN bloque par trop d'essais se debloque.
        pinService.debloquer(principal.getId());
        LoginResponse reponse = sessionService.ouvrir(principal);
        journal.connexion(principal);
        return reponse;
    }

    @GetMapping("/moi")
    @Operation(summary = "Retourne le profil de l'utilisateur connecte et les modules de son entreprise")
    public UtilisateurCourant moi(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return sessionService.profil(principal);
    }
}
