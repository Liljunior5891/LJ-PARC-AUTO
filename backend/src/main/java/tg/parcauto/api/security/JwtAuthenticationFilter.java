package tg.parcauto.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIXE = "Bearer ";

    private static final java.util.Set<String> CHEMINS_AVANT_CHANGEMENT = java.util.Set.of(
            "/api/auth/moi", "/api/auth/logout",
            "/api/utilisateurs/moi", "/api/utilisateurs/moi/mot-de-passe");

    private final JwtService jwtService;
    private final UtilisateurDetailsService utilisateurDetailsService;
    private final SessionService sessions;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String entete = request.getHeader("Authorization");
        if (entete != null && entete.startsWith(PREFIXE)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = entete.substring(PREFIXE.length());
            try {
                io.jsonwebtoken.Claims claims = jwtService.lire(token);
                UserDetails details = utilisateurDetailsService.loadUserByUsername(claims.getSubject());
                UtilisateurPrincipal compte = (UtilisateurPrincipal) details;
                // Le jeton vaut pour un compte precis (uid) et pour la version courante de ses jetons :
                // apres un changement de mot de passe ou d'adresse, les anciens ne rouvrent rien.
                Object uid = claims.get("uid");
                boolean memeCompte = uid instanceof Number n && n.longValue() == compte.getId();
                if (details.isEnabled() && memeCompte && !compte.jetonPerime(claims.get("ver"))) {
                    if (compte.isMotDePasseAChanger() && !permisAvantChangement(request)) {
                        motDePasseAChanger(response);
                        return;
                    }
                    if (sessions.deuxFacteursAActiver(compte) && !permisAvantChangement(request)
                            && !request.getRequestURI().startsWith("/api/auth/deux-facteurs")) {
                        refuser(response, "DEUX_FACTEURS_A_ACTIVER",
                                "Activez d'abord la double authentification depuis Mon compte");
                        return;
                    }
                    var auth = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    // L'entreprise vient du compte relu en base, jamais du jeton seul.
                    ContexteEntreprise.definir(((UtilisateurPrincipal) details).getEntrepriseId());
                }
            } catch (Exception ex) {
                log.debug("Jeton JWT rejete : {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Les fils du serveur sont recycles : le contexte ne doit pas survivre a la requete.
            ContexteEntreprise.effacer();
        }
    }

    /** Avec un mot de passe provisoire, seuls son compte et la deconnexion restent accessibles. */
    private static boolean permisAvantChangement(HttpServletRequest request) {
        // Liste exacte : un futur chemin commencant par le meme prefixe n'est pas ouvert par megarde.
        String chemin = request.getRequestURI();
        return CHEMINS_AVANT_CHANGEMENT.contains(chemin);
    }

    private static void refuser(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    private static void motDePasseAChanger(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"MOT_DE_PASSE_A_CHANGER\",\"message\":"
                + "\"Changez d'abord votre mot de passe provisoire depuis Mon compte\"}");
    }
}
