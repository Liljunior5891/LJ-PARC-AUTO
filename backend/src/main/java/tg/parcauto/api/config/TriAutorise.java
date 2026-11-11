package tg.parcauto.api.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tg.parcauto.api.common.BusinessException;

import java.util.regex.Pattern;

/**
 * Refuse un tri sur un champ interne ({@code ?sort=fichierChemin},
 * {@code ?sort=chauffeur.salaireMensuel}...). Trier sur un champ non affiche
 * permet d'en deviner l'ordre, donc les valeurs, sans jamais les voir.
 */
@Component
public class TriAutorise implements HandlerInterceptor {

    private static final Pattern INTERDIT = Pattern.compile(
            "(?i).*(chemin|empreinte|motdepasse|pinhash|jeton|secret|cleapi|token|entrepriseid|salaire|codeappairage).*");
    private static final Pattern FORME = Pattern.compile("^[A-Za-z0-9_.]{1,60}$");

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String[] tris = request.getParameterValues("sort");
        if (tris == null) {
            return true;
        }
        for (String tri : tris) {
            String propriete = tri.split(",")[0].trim();
            if (propriete.isEmpty()) {
                continue;
            }
            if (!FORME.matcher(propriete).matches() || INTERDIT.matcher(propriete).matches()) {
                throw new BusinessException("Critere de tri non autorise");
            }
        }
        return true;
    }
}
