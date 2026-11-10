package tg.parcauto.api.marketplace;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Pose l'entreprise d'une requete publique de la Marketplace : depuis l'adresse
 * de la boutique ({@code /api/public/boutiques/{slug}/...}) ou depuis le jeton
 * d'un lien de suivi ({@code /api/public/suivi/{jeton}/...}).
 * <p>
 * Le client n'a pas de compte : l'adresse ou le jeton suffisent a designer
 * l'entreprise. Ils sont lus en SQL direct, hors du cloisonnement d'Hibernate,
 * puisque l'entreprise n'est pas encore connue. Enregistre avant le controle
 * des modules, qui verifie ensuite que l'entreprise a bien la Marketplace.
 */
@Component
@RequiredArgsConstructor
public class ContexteBoutiqueInterceptor implements HandlerInterceptor {

    private static final String POSE = ContexteBoutiqueInterceptor.class.getName() + ".pose";
    private static final Pattern FORME_SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final Pattern FORME_JETON = Pattern.compile("^[a-f0-9]{32}$");

    private final JdbcTemplate jdbc;
    private final tg.parcauto.api.plateforme.abonnement.AbonnementService abonnements;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        @SuppressWarnings("unchecked")
        Map<String, String> variables =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables == null) {
            return true;
        }
        Long entreprise = null;
        String slug = variables.get("slug");
        String jeton = variables.get("jetonSuivi");
        if (slug != null) {
            if (!FORME_SLUG.matcher(slug).matches()) {
                throw new ResourceNotFoundException("Boutique introuvable");
            }
            // Une entreprise suspendue n'a plus de vitrine (comme sur la place de marche).
            entreprise = premier("""
                    select b.entreprise_id from mkt_boutique b
                    join entreprise e on e.id = b.entreprise_id and e.statut = 'ACTIVE'
                    where b.slug = ?
                    """, slug);
            if (entreprise == null) {
                throw new ResourceNotFoundException("Boutique introuvable");
            }
            // Abonnement expire (lecture seule) : la vitrine reste visible, mais ne prend plus de commande.
            if (!"GET".equalsIgnoreCase(request.getMethod()) && abonnements.lectureSeule(entreprise)) {
                throw new tg.parcauto.api.common.BusinessException(
                        "Cette boutique ne prend pas de commande en ligne pour le moment : contactez-la directement");
            }
        } else if (jeton != null) {
            if (!FORME_JETON.matcher(jeton).matches()) {
                throw new ResourceNotFoundException("Ce lien de suivi n'est pas reconnu");
            }
            entreprise = premier("""
                    select entreprise_id from mkt_commande where jeton = ?
                    union all
                    select entreprise_id from mkt_demande_location where jeton = ?
                    """, jeton, jeton);
            if (entreprise == null) {
                throw new ResourceNotFoundException("Ce lien de suivi n'est pas reconnu");
            }
        }
        if (entreprise != null) {
            ContexteEntreprise.definir(entreprise);
            request.setAttribute(POSE, Boolean.TRUE);
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        if (request.getAttribute(POSE) != null) {
            ContexteEntreprise.effacer();
        }
    }

    private Long premier(String sql, Object... parametres) {
        List<Long> trouves = jdbc.query(sql, (rs, i) -> rs.getLong(1), parametres);
        return trouves.isEmpty() ? null : trouves.get(0);
    }
}
