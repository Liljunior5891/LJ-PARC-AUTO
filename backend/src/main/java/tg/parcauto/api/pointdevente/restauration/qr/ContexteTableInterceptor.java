package tg.parcauto.api.pointdevente.restauration.qr;

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
 * Pose l'entreprise d'une requete publique a partir du jeton de table qu'elle
 * porte dans son adresse ({@code /api/public/qr/{jeton}/...}).
 * <p>
 * Le client n'a pas de compte : c'est le jeton, imprevisible, qui designe a la
 * fois l'etablissement et la table. Il est lu en SQL direct, hors du
 * cloisonnement d'Hibernate, puisque l'entreprise n'est pas encore connue.
 * Enregistre avant le controle des modules, qui verifie ensuite que
 * l'etablissement a bien un point de vente en mode restaurant.
 */
@Component
@RequiredArgsConstructor
public class ContexteTableInterceptor implements HandlerInterceptor {

    /** Attribut de requete portant l'identifiant de la table resolue. */
    public static final String TABLE = ContexteTableInterceptor.class.getName() + ".table";

    private static final Pattern FORME_JETON = Pattern.compile("^[a-f0-9]{32}$");

    private final JdbcTemplate jdbc;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        @SuppressWarnings("unchecked")
        Map<String, String> variables =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String jeton = variables == null ? null : variables.get("jeton");
        if (jeton == null) {
            return true;
        }
        if (!FORME_JETON.matcher(jeton).matches()) {
            throw new ResourceNotFoundException("Ce code QR n'est pas reconnu");
        }
        List<long[]> trouvees = jdbc.query(
                "select id, entreprise_id from pdv_table where jeton_qr = ? and actif",
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)}, jeton);
        if (trouvees.isEmpty()) {
            throw new ResourceNotFoundException("Ce code QR n'est plus valable : demandez a un serveur");
        }
        ContexteEntreprise.definir(trouvees.get(0)[1]);
        request.setAttribute(TABLE, trouvees.get(0)[0]);
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        if (request.getAttribute(TABLE) != null) {
            ContexteEntreprise.effacer();
        }
    }
}
