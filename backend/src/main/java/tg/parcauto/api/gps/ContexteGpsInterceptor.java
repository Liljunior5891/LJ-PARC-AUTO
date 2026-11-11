package tg.parcauto.api.gps;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tg.parcauto.api.common.LimiteurRequetes;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;

import java.time.Duration;

/**
 * Traceurs GPS sans compte : l'entreprise vient du jeton GPS (en-tete
 * X-Jeton-Gps ou parametre « jeton »), avant le controle du module.
 */
@Component
@RequiredArgsConstructor
public class ContexteGpsInterceptor implements HandlerInterceptor {

    static final String ENTREPRISE = ContexteGpsInterceptor.class.getName() + ".entreprise";

    private final JetonGpsService jetons;
    private final LimiteurRequetes limiteur;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        // Essais de jetons limites par adresse, avant toute recherche en base.
        limiteur.exiger("gps-ip:" + request.getRemoteAddr(), 600, Duration.ofMinutes(1),
                "Trop d'envois depuis cette adresse : ralentissez la passerelle");
        String entete = request.getHeader("X-Jeton-Gps");
        String jeton = entete != null && !entete.isBlank() ? entete : request.getParameter("jeton");
        Long entreprise = jetons.entreprise(jeton);
        if (entreprise == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"status\":401,\"message\":\"Jeton GPS inconnu\"}");
            } catch (java.io.IOException e) {
                // reponse deja engagee : le statut suffit
            }
            return false;
        }
        // Une passerelle envoie souvent, mais pas sans limite.
        limiteur.exiger("gps:" + entreprise, 1200, Duration.ofMinutes(1), "Trop d'envois de positions : ralentissez la passerelle");
        ContexteEntreprise.definir(entreprise);
        request.setAttribute(ENTREPRISE, entreprise);
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        if (request.getAttribute(ENTREPRISE) != null) {
            ContexteEntreprise.effacer();
        }
    }
}
