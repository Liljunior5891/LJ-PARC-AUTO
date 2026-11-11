package tg.parcauto.api.plateforme.abonnement;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;

import java.util.Set;

/**
 * Points publics a jeton (QR de table, boutique et suivi, pointage de presence,
 * bornes, traceurs GPS) : une fois l'entreprise trouvee par l'intercepteur de
 * contexte, applique les memes regles qu'aux comptes connectes.
 * <ul>
 *   <li>entreprise suspendue par la plateforme : plus aucun service ;</li>
 *   <li>abonnement expire (lecture seule) : les lectures restent, plus aucune ecriture.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AccesPublicInterceptor implements HandlerInterceptor {

    private static final Set<String> LECTURES = Set.of("GET", "HEAD", "OPTIONS");

    private final JdbcTemplate jdbc;
    private final AbonnementService abonnements;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        Long entreprise = ContexteEntreprise.courante().orElse(null);
        if (entreprise == null || ContexteEntreprise.systeme()) {
            return true;
        }
        Integer active = jdbc.queryForObject(
                "select count(*) from entreprise where id = ? and statut = 'ACTIVE'", Integer.class, entreprise);
        if (active == null || active == 0) {
            throw new ResourceNotFoundException("Ce service n'est pas disponible pour le moment");
        }
        if (!LECTURES.contains(request.getMethod()) && abonnements.lectureSeule(entreprise)) {
            throw new AbonnementExpireException();
        }
        return true;
    }
}
