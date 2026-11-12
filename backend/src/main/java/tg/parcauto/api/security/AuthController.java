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
    private final tg.parcauto.api.common.LimiteurRequetes limiteur;
    private final UtilisateurService comptes;
    private final JwtService jwtService;
    private final UtilisateurDetailsService utilisateurDetails;
    private final DeuxFacteursService deuxFacteurs;

    /** Echecs de connexion toleres avant blocage temporaire, par adresse IP et par compte. */
    private static final int ECHECS_MAX_IP = 20;
    private static final int ECHECS_MAX_COMPTE = 5;
    private static final java.time.Duration FENETRE_ECHECS = java.time.Duration.ofMinutes(15);
    private static final String TROP_D_ESSAIS = "Trop de tentatives de connexion. Reessayez dans quelques minutes.";

    public record LoginRequest(
            @NotBlank @Email @jakarta.validation.constraints.Size(max = 254) String email,
            // BCrypt ne lit que 72 octets : au-dela, deux mots de passe differents seraient egaux.
            @NotBlank @jakarta.validation.constraints.Size(max = 72) String motDePasse) {
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
            boolean motDePasseAChanger,
            /** Double authentification active sur ce compte. */
            boolean deuxFacteursActif,
            /** Super-administrateur sans double authentification : il doit l'activer avant tout. */
            boolean deuxFacteursAActiver) {
    }

    /**
     * Reponse de connexion. Avec la double authentification, la premiere etape ne renvoie
     * que {@code jetonDeuxFacteurs} : la session s'ouvre avec le code (POST /deux-facteurs).
     */
    public record LoginResponse(String token, long expiresIn, UtilisateurCourant utilisateur, String jetonDeuxFacteurs) {
        public LoginResponse(String token, long expiresIn, UtilisateurCourant utilisateur) {
            this(token, expiresIn, utilisateur, null);
        }
    }

    public record CodeDeuxFacteurs(@NotBlank @jakarta.validation.constraints.Size(max = 2000) String jeton,
                                   @NotBlank @jakarta.validation.constraints.Size(max = 20) String code) {
    }

    @PostMapping("/login")
    @Operation(summary = "Authentifie un utilisateur et retourne un jeton JWT")
    public LoginResponse login(@Valid @RequestBody LoginRequest request,
                               jakarta.servlet.http.HttpServletRequest http) {
        String cleIp = "login-ip:" + http.getRemoteAddr();
        String cleCompte = "login-compte:" + request.email().trim().toLowerCase(java.util.Locale.ROOT);
        // Bloque avant toute verification du mot de passe : ni essai, ni ligne de journal.
        limiteur.verifier(cleIp, ECHECS_MAX_IP, FENETRE_ECHECS, TROP_D_ESSAIS);
        limiteur.verifier(cleCompte, ECHECS_MAX_COMPTE, FENETRE_ECHECS, TROP_D_ESSAIS);
        if (request.motDePasse().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            limiteur.noter(cleIp);
            throw new org.springframework.security.authentication.BadCredentialsException("Identifiants invalides");
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.motDePasse()));
        } catch (org.springframework.security.core.AuthenticationException e) {
            limiteur.noter(cleIp);
            limiteur.noter(cleCompte);
            // Journal d'audit de l'entreprise du compte vise, s'il existe.
            // Compte du super-admin : journal general de la plateforme (entreprise vide).
            utilisateurs.findByEmailIgnoreCase(request.email())
                    .ifPresent(u -> journal.echecConnexion(u.getEntrepriseId(), u.getId(), u.nomComplet(),
                            e instanceof org.springframework.security.authentication.DisabledException ? "compte desactive" : "mot de passe incorrect"));
            throw e;
        }
        UtilisateurPrincipal principal = (UtilisateurPrincipal) authentication.getPrincipal();
        limiteur.oublier(cleCompte);
        if (principal.isDeuxFacteursActif()) {
            // Mot de passe bon : la session ne s'ouvre qu'avec le code de l'application.
            return new LoginResponse(null, 0, null, jwtService.genererJetonDeuxFacteurs(principal));
        }
        // Le mot de passe prouve l'identite : le PIN bloque par trop d'essais se debloque.
        pinService.debloquer(principal.getId());
        LoginResponse reponse = sessionService.ouvrir(principal);
        journal.connexion(principal);
        return reponse;
    }

    /** Seconde etape de la connexion : le code de l'application d'authentification (ou un code de secours). */
    @PostMapping("/deux-facteurs")
    @Operation(summary = "Ouvre la session avec le code de double authentification")
    public LoginResponse deuxFacteurs(@Valid @RequestBody CodeDeuxFacteurs demande) {
        io.jsonwebtoken.Claims claims;
        try {
            claims = jwtService.lireJetonDeuxFacteurs(demande.jeton());
        } catch (RuntimeException e) {
            throw new org.springframework.security.authentication.BadCredentialsException(
                    "Delai depasse : reconnectez-vous avec votre mot de passe");
        }
        UtilisateurPrincipal principal = (UtilisateurPrincipal) utilisateurDetails.loadUserByUsername(claims.getSubject());
        Object uid = claims.get("uid");
        if (!principal.isEnabled() || !(uid instanceof Number n && n.longValue() == principal.getId())
                || principal.jetonPerime(claims.get("ver"))) {
            throw new org.springframework.security.authentication.BadCredentialsException("Session refusee");
        }
        deuxFacteurs.verifierConnexion(principal.getId(), demande.code());
        pinService.debloquer(principal.getId());
        LoginResponse reponse = sessionService.ouvrir(principal);
        journal.connexion(principal);
        return reponse;
    }

    /**
     * Deconnexion : invalide cote serveur tous les jetons du compte (un jeton
     * copie avant la deconnexion sur un poste partage ne rouvre plus rien).
     */
    @PostMapping("/logout")
    @Operation(summary = "Ferme la session : les jetons deja emis pour ce compte ne sont plus acceptes")
    public void logout(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        if (principal != null) {
            comptes.deconnecter(principal.getId());
        }
    }

    @GetMapping("/moi")
    @Operation(summary = "Retourne le profil de l'utilisateur connecte et les modules de son entreprise")
    public UtilisateurCourant moi(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return sessionService.profil(principal);
    }
}
