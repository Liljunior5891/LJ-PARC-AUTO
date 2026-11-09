package tg.parcauto.api.presence;

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
 * Pose l'entreprise d'une requete de pointage sans compte, a partir du jeton
 * qu'elle porte : celui du lieu (adresse du code QR) ou celui de la borne
 * (remis a l'appairage, dont seule l'empreinte est stockee). Lu en SQL direct,
 * hors du cloisonnement d'Hibernate, puisque l'entreprise n'est pas encore connue.
 */
@Component
@RequiredArgsConstructor
public class ContextePresenceInterceptor implements HandlerInterceptor {

    public static final String LIEU = ContextePresenceInterceptor.class.getName() + ".lieu";
    public static final String BORNE = ContextePresenceInterceptor.class.getName() + ".borne";
    public static final String ENTREPRISE = ContextePresenceInterceptor.class.getName() + ".entreprise";

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
            throw new ResourceNotFoundException("Ce code de pointage n'est pas reconnu");
        }
        boolean borne = request.getRequestURI().contains("/api/public/presence/bornes/");
        List<long[]> trouves = borne
                ? jdbc.query("select id, entreprise_id from pres_borne where jeton_empreinte = ? and actif",
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)}, SecuritePresence.sha256(jeton))
                : jdbc.query("select id, entreprise_id from pres_lieu where jeton_public = ? and actif",
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)}, jeton);
        if (trouves.isEmpty()) {
            throw new ResourceNotFoundException(borne
                    ? "Cette borne n'est plus appairee : demandez un nouveau code au gerant"
                    : "Ce code QR n'est plus valable : scannez celui de la borne");
        }
        ContexteEntreprise.definir(trouves.get(0)[1]);
        request.setAttribute(borne ? BORNE : LIEU, trouves.get(0)[0]);
        request.setAttribute(ENTREPRISE, jdbc.queryForObject("select nom from entreprise where id = ?",
                String.class, trouves.get(0)[1]));
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        if (request.getAttribute(LIEU) != null || request.getAttribute(BORNE) != null) {
            ContexteEntreprise.effacer();
        }
    }
}
