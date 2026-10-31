package tg.parcauto.api.plateforme.module;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;

/**
 * Refuse l'acces aux controleurs d'un module que l'entreprise n'a pas active.
 * <p>
 * L'exception levee ici passe par le gestionnaire d'erreurs commun et revient
 * au client en 403 avec un message explicite.
 */
@Component
@RequiredArgsConstructor
public class ControleModuleInterceptor implements HandlerInterceptor {

    private final ModuleService moduleService;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (!(handler instanceof HandlerMethod methode)) {
            return true;
        }
        ModuleRequis requis = AnnotatedElementUtils.findMergedAnnotation(methode.getBeanType(), ModuleRequis.class);
        if (requis == null) {
            return true;
        }
        Long entrepriseId = ContexteEntreprise.courante().orElse(null);
        if (!moduleService.estActif(entrepriseId, requis.value())) {
            throw new ModuleNonActiveException(requis.value());
        }
        if (!requis.variante().isEmpty()
                && !requis.variante().equals(moduleService.variante(entrepriseId, requis.value()))) {
            throw new ModuleNonActiveException(requis.value(), requis.variante());
        }
        // Le module est actif pour l'entreprise, encore faut-il que le profil y ait sa place.
        if (!requis.publique() && !profilAutorise(requis.value())) {
            throw new AccessDeniedException("Votre profil n'a pas acces au module " + requis.value().getLibelle());
        }
        return true;
    }

    private boolean profilAutorise(ModuleApplicatif module) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .anyMatch(role -> module.getProfils().stream().anyMatch(p -> p.name().equals(role)));
    }
}
