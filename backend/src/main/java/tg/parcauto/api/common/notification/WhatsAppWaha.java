package tg.parcauto.api.common.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tg.parcauto.api.common.BusinessException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fournisseur WhatsApp : WAHA (WhatsApp HTTP API), un serveur auto-heberge qui
 * pilote un compte WhatsApp relie par code QR.
 * <p>
 * Le reglage (adresse, cle d'API, session) est fait par le super-administrateur
 * et lu en base (table plateforme_whatsapp), garde 30 secondes en memoire.
 * Desactive, les messages WhatsApp restent simules.
 */
@Slf4j
@Component
public class WhatsAppWaha {

    /** Reglage de la plateforme. */
    public record Reglage(boolean actif, String url, String cleApi, String session, int pauseMs) {
    }

    private static final long DUREE_CACHE_MS = 30_000;

    private final JdbcTemplate jdbc;
    private final CoffreCleWhatsApp coffre;
    private volatile Reglage cache;
    private volatile long cacheLu;
    /** Dernier envoi : les messages sont espaces de la pause reglee. */
    private long dernierEnvoi;

    public WhatsAppWaha(JdbcTemplate jdbc, CoffreCleWhatsApp coffre) {
        this.jdbc = jdbc;
        this.coffre = coffre;
    }

    /** Une cle enregistree en clair (avant le chiffrement) est chiffree au demarrage. */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void chiffrerCleExistante() {
        try {
            List<String> cles = jdbc.queryForList("select cle_api from plateforme_whatsapp where id = 1 and cle_api is not null",
                    String.class);
            if (!cles.isEmpty() && !coffre.estChiffree(cles.get(0))) {
                jdbc.update("update plateforme_whatsapp set cle_api = ? where id = 1", coffre.chiffrer(cles.get(0)));
                log.info("Cle d'API WAHA chiffree en base");
            }
        } catch (RuntimeException e) {
            log.warn("Chiffrement de la cle WAHA impossible : {}", e.getMessage());
        }
    }

    /** Valeur a enregistrer en base pour une cle saisie. */
    public String cleAStocker(String clair) {
        return coffre.chiffrer(clair);
    }

    public Reglage reglage() {
        Reglage r = cache;
        if (r == null || System.currentTimeMillis() - cacheLu > DUREE_CACHE_MS) {
            List<Reglage> lignes = jdbc.query("select actif, url, cle_api, session, pause_ms from plateforme_whatsapp where id = 1",
                    (rs, i) -> new Reglage(rs.getBoolean(1), rs.getString(2), coffre.dechiffrer(rs.getString(3)),
                            rs.getString(4), rs.getInt(5)));
            r = lignes.isEmpty() ? new Reglage(false, "", null, "default", 1500) : lignes.get(0);
            cache = r;
            cacheLu = System.currentTimeMillis();
        }
        return r;
    }

    /** A appeler apres une modification du reglage. */
    public void oublierReglage() {
        cache = null;
    }

    /** Vrai si les messages WhatsApp partent vraiment. */
    public boolean actif() {
        Reglage r = reglage();
        return r.actif() && r.url() != null && !r.url().isBlank();
    }

    /** Envoie un texte ; leve une exception en cas d'echec (le message sera retente). */
    public void envoyer(String telephone, String texte) {
        Reglage r = reglage();
        espacer(r.pauseMs());
        poster(r, telephone, texte);
    }

    private void poster(Reglage r, String telephone, String texte) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("session", r.session());
        corps.put("chatId", chatId(telephone));
        corps.put("text", texte);
        client(r).post().uri("/api/sendText").contentType(MediaType.APPLICATION_JSON).body(corps)
                .retrieve().toBodilessEntity();
    }

    // --- Gestion de la session (console du super-administrateur) ---

    /** Etat de la session : STOPPED, STARTING, SCAN_QR_CODE, WORKING, FAILED, ou ABSENTE. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> etatSession() {
        Reglage r = reglage();
        try {
            return client(r).get().uri("/api/sessions/{nom}", r.session()).retrieve().body(Map.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Map.of("name", r.session(), "status", "ABSENTE");
            }
            throw traduire(e, r);
        } catch (RestClientException e) {
            throw traduire(e, r);
        }
    }

    /** Cree et demarre la session (ou la redemarre si elle existe deja). */
    public void demarrer() {
        Reglage r = reglage();
        try {
            client(r).post().uri("/api/sessions").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("name", r.session(), "start", true)).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            // Session deja existante (arretee ou en echec) : on la redemarre ; « start » ne relance pas une session en echec.
            if (e.getStatusCode().value() == 409 || e.getStatusCode().value() == 422 || e.getStatusCode().value() == 400) {
                try {
                    client(r).post().uri("/api/sessions/{nom}/restart", r.session()).retrieve().toBodilessEntity();
                    return;
                } catch (RestClientException e2) {
                    throw traduire(e2, r);
                }
            }
            throw traduire(e, r);
        } catch (RestClientException e) {
            throw traduire(e, r);
        }
    }

    /** Code QR a scanner depuis WhatsApp (Appareils connectes), en image PNG encodee en base 64. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> codeQr() {
        Reglage r = reglage();
        try {
            return client(r).get().uri("/api/{nom}/auth/qr?format=image", r.session())
                    .accept(MediaType.APPLICATION_JSON).retrieve().body(Map.class);
        } catch (RestClientException e) {
            throw traduire(e, r);
        }
    }

    /** Delie le compte WhatsApp de la session. */
    public void deconnecter() {
        Reglage r = reglage();
        try {
            client(r).post().uri("/api/sessions/{nom}/logout", r.session()).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw traduire(e, r);
        }
    }

    /** Message d'essai, envoye tout de suite (sans file d'attente) pour voir l'erreur eventuelle. */
    public void essayer(String telephone, String texte) {
        String numero = NotificationService.normaliser(NotificationService.Canal.WHATSAPP, telephone);
        if (numero == null) {
            throw new BusinessException("Numero invalide : saisissez-le avec l'indicatif, par exemple +22890000000.");
        }
        Reglage r = reglage();
        try {
            // Envoi immediat, sans la pause entre messages : la requete du super-administrateur n'attend pas.
            poster(r, numero, texte);
        } catch (RestClientException e) {
            throw traduire(e, r);
        }
    }

    /** Identifiant WhatsApp d'un numero : chiffres seuls, suivis de @c.us. */
    static String chatId(String telephone) {
        return telephone.replaceAll("[^0-9]", "") + "@c.us";
    }

    private synchronized void espacer(int pauseMs) {
        long attente = dernierEnvoi + pauseMs - System.currentTimeMillis();
        if (attente > 0) {
            try {
                Thread.sleep(attente);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        dernierEnvoi = System.currentTimeMillis();
    }

    private static RestClient client(Reglage r) {
        if (r.url() == null || r.url().isBlank()) {
            throw new BusinessException("Adresse du serveur WAHA non renseignee.");
        }
        // Pas de redirection : la cle d'API ne doit partir que vers l'adresse reglee.
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connexion, String methode) throws java.io.IOException {
                super.prepareConnection(connexion, methode);
                connexion.setInstanceFollowRedirects(false);
            }
        };
        fabrique.setConnectTimeout(5_000);
        fabrique.setReadTimeout(20_000);
        RestClient.Builder b = RestClient.builder().baseUrl(r.url().replaceAll("/+$", "")).requestFactory(fabrique)
                .defaultStatusHandler(HttpStatusCode::is3xxRedirection, (requete, reponse) -> {
                    throw new RestClientException("Redirection refusee (" + reponse.getStatusCode().value()
                            + ") : verifiez l'adresse du serveur WAHA");
                });
        if (r.cleApi() != null && !r.cleApi().isBlank()) {
            b.defaultHeader("X-Api-Key", r.cleApi());
        }
        return b.build();
    }

    private static BusinessException traduire(RestClientException e, Reglage r) {
        if (e instanceof RestClientResponseException re) {
            HttpStatusCode code = re.getStatusCode();
            if (code.value() == 401 || code.value() == 403) {
                return new BusinessException("WAHA refuse la cle d'API : verifiez-la.");
            }
            // Le corps de la reponse n'est pas recopie a l'ecran (il pourrait venir d'un autre serveur) : journal seulement.
            String detail = re.getResponseBodyAsString();
            log.warn("WAHA a repondu {} : {}", code.value(), detail.length() > 300 ? detail.substring(0, 300) : detail);
            return new BusinessException("WAHA a repondu " + code.value() + " : voir le journal du serveur.");
        }
        log.warn("WAHA injoignable ({}) : {}", r.url(), e.getMessage());
        if (e.getMessage() != null && e.getMessage().startsWith("Redirection refusee")) {
            return new BusinessException(e.getMessage() + ".");
        }
        return new BusinessException("Serveur WAHA injoignable a l'adresse " + r.url() + ".");
    }
}
