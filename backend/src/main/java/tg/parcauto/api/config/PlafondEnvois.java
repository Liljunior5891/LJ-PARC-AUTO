package tg.parcauto.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Plafond de taille des envois, avant toute lecture du corps.
 * <p>
 * Le reglage multipart global (65 Mo) n'existe que pour l'import ZIP des
 * produits avec leurs photos. Partout ailleurs, y compris sur les points publics
 * sans connexion, une requete de plus de 10 Mo est refusee en 413 sans etre lue.
 * Une requete sans longueur annoncee (envoi par morceaux) n'est acceptee que
 * hors multipart.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PlafondEnvois extends OncePerRequestFilter {

    static final long PLAFOND_COURANT = 10L * 1024 * 1024;
    static final long PLAFOND_IMPORT = 65L * 1024 * 1024;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest requete, @NonNull HttpServletResponse reponse,
                                    @NonNull FilterChain suite) throws ServletException, IOException {
        long longueur = requete.getContentLengthLong();
        String type = requete.getContentType();
        boolean multipart = type != null && type.toLowerCase(java.util.Locale.ROOT).startsWith("multipart/");
        long plafond = estImport(requete) ? PLAFOND_IMPORT : PLAFOND_COURANT;
        if (longueur > plafond || (multipart && longueur < 0)) {
            reponse.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            reponse.setContentType("application/json;charset=UTF-8");
            reponse.getWriter().write("{\"message\":\"Fichier ou requete trop volumineux\"}");
            return;
        }
        suite.doFilter(requete, reponse);
    }

    private static boolean estImport(HttpServletRequest requete) {
        return "POST".equals(requete.getMethod()) && requete.getRequestURI().startsWith("/api/pdv/rapports/import/");
    }
}
