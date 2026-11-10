package tg.parcauto.api.plateforme.abonnement;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;

import java.util.Set;

/**
 * Abonnement expire (grace epuisee) : l'entreprise consulte tout mais ne saisit
 * plus rien. Seules les lectures passent ; restent ouverts la connexion, les
 * pages publiques et le menu Mon abonnement, pour pouvoir payer.
 */
@Component
@RequiredArgsConstructor
public class LectureSeuleInterceptor implements HandlerInterceptor {

    private static final Set<String> LECTURES = Set.of("GET", "HEAD", "OPTIONS");

    private final AbonnementService service;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (LECTURES.contains(request.getMethod()) || ContexteEntreprise.systeme()) {
            return true;
        }
        Long entrepriseId = ContexteEntreprise.courante().orElse(null);
        if (entrepriseId != null && service.lectureSeule(entrepriseId)) {
            throw new AbonnementExpireException();
        }
        return true;
    }
}
