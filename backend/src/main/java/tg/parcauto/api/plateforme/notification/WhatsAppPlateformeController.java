package tg.parcauto.api.plateforme.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.common.audit.EcritureJournal;
import tg.parcauto.api.common.audit.JournalActiviteService;
import tg.parcauto.api.common.notification.WhatsAppWaha;
import tg.parcauto.api.security.Autorisations;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reglage de WhatsApp (serveur WAHA) par le super-administrateur : adresse,
 * cle d'API, session, activation ; liaison du compte WhatsApp par code QR et
 * message d'essai.
 */
@RestController
@RequestMapping("/api/plateforme/whatsapp")
@PreAuthorize(Autorisations.PLATEFORME)
@RequiredArgsConstructor
@Tag(name = "Plateforme - WhatsApp (WAHA)")
public class WhatsAppPlateformeController {

    private final JdbcTemplate jdbc;
    private final WhatsAppWaha waha;
    private final JournalActiviteService journal;
    private final tg.parcauto.api.common.LimiteurRequetes limiteur;

    /** Reglage affiche : la cle n'est jamais renvoyee, seulement sa presence. */
    public record Vue(boolean actif, String url, boolean cleDefinie, String session, int pauseMs) {
    }

    /**
     * Modification ; une cle vide garde la cle enregistree.
     */
    public record Modification(boolean actif,
                               @NotBlank @Size(max = 200) @Pattern(regexp = "^https?://[^\\s]+$",
                                       message = "Adresse http:// ou https:// attendue") String url,
                               @Size(max = 200) String cleApi,
                               @NotBlank @Size(max = 60) @Pattern(regexp = "^[A-Za-z0-9_-]+$") String session,
                               @Min(0) @Max(60_000) int pauseMs) {
    }

    public record Essai(@NotBlank @Size(max = 30) String telephone) {
    }

    @GetMapping
    @Operation(summary = "Reglage actuel")
    public Vue lire() {
        WhatsAppWaha.Reglage r = waha.reglage();
        return new Vue(r.actif(), r.url(), r.cleApi() != null && !r.cleApi().isBlank(), r.session(), r.pauseMs());
    }

    @PutMapping
    @Operation(summary = "Enregistre le reglage")
    public Vue enregistrer(@Valid @RequestBody Modification m) {
        Vue avant = lire();
        String cle = m.cleApi() == null || m.cleApi().isBlank() ? null : m.cleApi().trim();
        String url = m.url().trim().replaceAll("/+$", "");
        if (url.matches("^https?://[^/]*@.*")) {
            throw new tg.parcauto.api.common.BusinessException("Adresse refusee : pas d'identifiants dans l'adresse du serveur.");
        }
        // La cle enregistree ne part jamais vers une nouvelle adresse : elle doit etre ressaisie.
        if (cle == null && avant.cleDefinie() && !url.equalsIgnoreCase(avant.url())) {
            throw new tg.parcauto.api.common.BusinessException(
                    "Vous changez l'adresse du serveur : ressaisissez la cle d'API pour cette adresse.");
        }
        jdbc.update("""
                update plateforme_whatsapp set actif = ?, url = ?, cle_api = coalesce(?, cle_api), session = ?, pause_ms = ?,
                       modifie_le = now(), modifie_par = ?
                where id = 1
                """, m.actif(), url, cle == null ? null : waha.cleAStocker(cle), m.session().trim(), m.pauseMs(),
                EcritureJournal.contexte().auteurNom());
        waha.oublierReglage();
        Vue apres = lire();
        journal.plateforme("MODIFICATION", "ReglageWhatsApp", "WhatsApp (WAHA)", 1L,
                "Reglage WhatsApp modifie" + (cle != null ? " (nouvelle cle d'API)" : ""), resume(avant), resume(apres));
        return apres;
    }

    @GetMapping("/session")
    @Operation(summary = "Etat de la session WAHA (SCAN_QR_CODE, WORKING...) et compte relie")
    public Map<String, Object> session() {
        Map<String, Object> s = waha.etatSession();
        Map<String, Object> vue = new LinkedHashMap<>();
        vue.put("statut", s.get("status"));
        if (s.get("me") instanceof Map<?, ?> me) {
            vue.put("numero", me.get("id"));
            vue.put("nom", me.get("pushName"));
        }
        return vue;
    }

    @PostMapping("/session/demarrer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cree ou redemarre la session")
    public void demarrer() {
        waha.demarrer();
        journal.plateforme("DEMARRAGE", "ReglageWhatsApp", "WhatsApp (WAHA)", 1L, "Session WhatsApp demarree", null, null);
    }

    @GetMapping("/session/qr")
    @Operation(summary = "Code QR a scanner dans WhatsApp, Appareils connectes (image PNG en base 64)")
    public Map<String, Object> qr() {
        Map<String, Object> q = waha.codeQr();
        return Map.of("mimetype", String.valueOf(q.getOrDefault("mimetype", "image/png")),
                "data", String.valueOf(q.getOrDefault("data", "")));
    }

    @PostMapping("/session/deconnecter")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delie le compte WhatsApp")
    public void deconnecter() {
        waha.deconnecter();
        journal.plateforme("DECONNEXION", "ReglageWhatsApp", "WhatsApp (WAHA)", 1L, "Compte WhatsApp delie", null, null);
    }

    @PostMapping("/essai")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Envoie tout de suite un message d'essai")
    public void essai(@Valid @RequestBody Essai e) {
        limiteur.exiger("whatsapp-essai-plateforme", 5, java.time.Duration.ofMinutes(10),
                "Trop de messages d'essai : patientez quelques minutes");
        waha.essayer(e.telephone(), "*Message d'essai*\nLes notifications WhatsApp de Nova fonctionnent.\n\n_Nova_");
    }

    private static Map<String, String> resume(Vue v) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("actif", String.valueOf(v.actif()));
        m.put("url", v.url());
        m.put("session", v.session());
        m.put("pauseMs", String.valueOf(v.pauseMs()));
        return m;
    }
}
