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
    private final tg.parcauto.api.plateforme.maintenance.MaintenanceService maintenances;

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
        // Module en maintenance : ecrans et points publics refuses a tous (503), sauf la reception
        // des positions GPS, que les traceurs envoient sans personne pour les rejouer.
        if (!request.getRequestURI().startsWith("/api/public/gps/")) {
            maintenances.enCours(requis.value()).ifPresent(m -> {
                throw new tg.parcauto.api.plateforme.maintenance.MaintenanceEnCoursException(requis.value(), m);
            });
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
        // Un compte qui n'a que le profil associe ne voit que sa part des benefices.
        if (!requis.publique() && seulementAssocie(requis.value())
                && AnnotatedElementUtils.findMergedAnnotation(methode.getBeanType(), OuvertAuxAssocies.class) == null) {
            throw new AccessDeniedException("Votre profil d'associe ne donne acces qu'a votre part des benefices");
        }
        return true;
    }

    /** Parmi les profils du module, le compte n'a que celui d'associe. */
    private boolean seulementAssocie(ModuleApplicatif module) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        java.util.Set<String> roles = auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .filter(role -> module.getProfils().stream().anyMatch(p -> p.name().equals(role)))
                .collect(java.util.stream.Collectors.toSet());
        return roles.equals(java.util.Set.of(tg.parcauto.api.security.Role.ASSOCIE.name()));
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
