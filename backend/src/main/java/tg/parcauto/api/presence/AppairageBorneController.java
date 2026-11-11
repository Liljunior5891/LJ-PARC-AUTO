package tg.parcauto.api.presence;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleService;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Appairage d'une borne : la tablette saisit le code a 6 chiffres affiche au
 * gerant et recoit son jeton. Le code designe l'entreprise : elle n'est connue
 * qu'apres sa lecture, en SQL direct.
 */
@RestController
@RequestMapping("/api/public/presence/appairage")
@RequiredArgsConstructor
@Tag(name = "Presence - appairage des bornes")
public class AppairageBorneController {

    private final JdbcTemplate jdbc;
    private final ModuleService modules;
    private final PointageService service;
    private final SecuritePresence securite;

    public record Demande(String code) {
    }

    /** Codes d'appairage expires : effaces, pour liberer l'index unique et ne rien laisser a deviner. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3_600_000, initialDelay = 300_000)
    public void purgerCodesExpires() {
        jdbc.update("update pres_borne set code_appairage = null, code_expire_le = null"
                + " where code_appairage is not null and code_expire_le < now()");
    }

    @PostMapping
    @Operation(summary = "Echange le code d'appairage contre le jeton de la borne")
    public PointageService.Appairage appairer(@RequestBody Demande demande, jakarta.servlet.http.HttpServletRequest requete) {
        String appareil = "appairage:" + requete.getRemoteAddr();
        long essai = securite.reserverEssai(appareil);
        // Compteur global des essais d'appairage : une enumeration repartie sur plusieurs adresses
        // s'arrete aussi (les codes en attente sont cherches dans toutes les entreprises).
        long essaiGlobal = securite.reserverEssaiEntreprise(0L);
        String code = demande.code() == null ? "" : demande.code().trim();
        List<long[]> trouvees = code.matches("\\d{6}")
                ? jdbc.query("select id, entreprise_id from pres_borne where code_appairage = ? and code_expire_le > ? and actif",
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)}, code, Timestamp.from(Instant.now()))
                : List.of();
        if (trouvees.isEmpty()) {
            throw new BusinessException("Code inconnu ou expire : demandez un nouveau code au gerant");
        }
        try {
            ContexteEntreprise.definir(trouvees.get(0)[1]);
            if (!modules.estActif(trouvees.get(0)[1], ModuleApplicatif.PRESENCE)) {
                throw new BusinessException("Le module Gestion de presence n'est pas active pour cette entreprise");
            }
            securite.reussi(appareil, essai);
            securite.reussi("entreprise:0", essaiGlobal);
            return service.appairer(trouvees.get(0)[0]);
        } finally {
            ContexteEntreprise.effacer();
        }
    }
}
