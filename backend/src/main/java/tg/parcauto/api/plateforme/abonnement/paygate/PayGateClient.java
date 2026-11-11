package tg.parcauto.api.plateforme.abonnement.paygate;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tg.parcauto.api.common.BusinessException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client de l'agregateur PayGate Global (Togo : Flooz de Moov Africa, T-Money de Togocom).
 * <ul>
 *   <li>{@code POST /api/v1/pay} : demande de paiement envoyee au telephone du client, qui la
 *       valide avec son code secret mobile money ;</li>
 *   <li>{@code POST /api/v2/status} : etat d'un paiement, retrouve par notre identifiant.</li>
 * </ul>
 * La cle (auth_token) vient de PAYGATE_AUTH_TOKEN et ne quitte jamais le serveur : pas de page
 * de paiement hebergee, qui la mettrait dans l'adresse visible du client.
 */
@Slf4j
@Component
public class PayGateClient {

    public static final String FLOOZ = "FLOOZ";
    public static final String TMONEY = "TMONEY";

    /** Codes de reponse de la demande de paiement. */
    public static final int DEMANDE_ENREGISTREE = 0;
    /** Codes de l'etat d'un paiement. */
    public static final int PAYE = 0;
    public static final int EN_COURS = 2;
    public static final int EXPIRE = 4;
    public static final int ANNULE = 6;

    private final String cle;
    private final RestClient http;

    public PayGateClient(@Value("${parcauto.paygate.auth-token:}") String cle,
                         @Value("${parcauto.paygate.url:https://paygateglobal.com}") String url) {
        this.cle = cle == null ? "" : cle.trim();
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(10_000);
        fabrique.setReadTimeout(30_000);
        this.http = RestClient.builder().baseUrl(url).requestFactory(fabrique).build();
    }

    /** Vrai si une cle PayGate est configuree : sinon le paiement en ligne n'est pas propose. */
    public boolean actif() {
        return !cle.isEmpty();
    }

    /** Reponse de la demande de paiement. */
    public record Demande(int statut, String txReference) {
    }

    /** Etat d'un paiement chez PayGate. */
    public record Etat(int statut, String txReference, String referencePaiement, String moyen) {
    }

    public Demande demanderPaiement(String telephone, long montant, String identifiant, String reseau,
                                    String description) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("phone_number", telephone);
        corps.put("amount", montant);
        corps.put("description", description);
        corps.put("identifier", identifiant);
        corps.put("network", reseau);
        JsonNode r = appeler("/api/v1/pay", corps);
        return new Demande(r.path("status").asInt(-1), texte(r, "tx_reference"));
    }

    public Etat etat(String identifiant) {
        JsonNode r = appeler("/api/v2/status", Map.of("identifier", identifiant));
        return new Etat(r.path("status").asInt(-1), texte(r, "tx_reference"), texte(r, "payment_reference"),
                texte(r, "payment_method"));
    }

    private JsonNode appeler(String chemin, Map<String, Object> corps) {
        if (!actif()) {
            throw new BusinessException("Le paiement en ligne n'est pas configure sur la plateforme");
        }
        Map<String, Object> avecCle = new LinkedHashMap<>();
        avecCle.put("auth_token", cle);
        avecCle.putAll(corps);
        try {
            JsonNode reponse = http.post().uri(chemin).contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON).body(avecCle).retrieve().body(JsonNode.class);
            if (reponse == null) {
                throw new BusinessException("PayGate n'a pas repondu : reessayez dans un instant");
            }
            return reponse;
        } catch (RestClientException e) {
            // Jamais la cle ni le corps dans les journaux.
            log.warn("Appel PayGate {} en echec : {}", chemin, e.getClass().getSimpleName());
            throw new BusinessException("PayGate est injoignable pour le moment : reessayez dans un instant");
        }
    }

    private static String texte(JsonNode n, String champ) {
        JsonNode v = n.get(champ);
        if (v == null || v.isNull()) {
            return null;
        }
        String t = v.asText().replaceAll("\\p{Cntrl}", "").trim();
        return t.isEmpty() ? null : (t.length() > 64 ? t.substring(0, 64) : t);
    }
}
