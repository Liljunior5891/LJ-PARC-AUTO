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

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String motDePasse) {
    }

    public record UtilisateurCourant(
            Long id, String email, String nomComplet, Long chauffeurId, List<String> roles) {
    }

    public record LoginResponse(String token, long expiresIn, UtilisateurCourant utilisateur) {
    }

    @PostMapping("/login")
    @Operation(summary = "Authentifie un utilisateur et retourne un jeton JWT")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.motDePasse()));
        UtilisateurPrincipal principal = (UtilisateurPrincipal) authentication.getPrincipal();
        return new LoginResponse(jwtService.genererToken(principal), jwtService.expirationSecondes(),
                versDto(principal));
    }

    @GetMapping("/moi")
    @Operation(summary = "Retourne le profil de l'utilisateur connecte")
    public UtilisateurCourant moi(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return versDto(principal);
    }

    private UtilisateurCourant versDto(UtilisateurPrincipal p) {
        List<String> roles = p.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .toList();
        return new UtilisateurCourant(p.getId(), p.getEmail(), p.getNomComplet(), p.getChauffeurId(), roles);
    }
}
