package tg.parcauto.api.plateforme.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.common.notification.NotificationService;
import tg.parcauto.api.security.Autorisations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Notifications envoyees par la plateforme : suivi, lecture des messages simules, renvoi. */
@RestController
@RequestMapping("/api/plateforme/notifications")
@PreAuthorize(Autorisations.PLATEFORME)
@RequiredArgsConstructor
@Tag(name = "Plateforme - notifications")
public class NotificationPlateformeController {

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    public record Ligne(Long id, Long entrepriseId, String entrepriseNom, String canal, String destinataire,
                        String sujet, String texte, String motif, String statut, String fournisseur, int tentatives,
                        String erreur, Instant creeLe, Instant envoyeLe) {
    }

    public record Etat(boolean emailBranche, boolean smsBranche, boolean whatsappBranche) {
    }

    @GetMapping("/etat")
    @Operation(summary = "Fournisseurs branches par canal (sinon les messages sont simules)")
    public Etat etat() {
        return new Etat(notifications.fournisseurActif(NotificationService.Canal.EMAIL),
                notifications.fournisseurActif(NotificationService.Canal.SMS),
                notifications.fournisseurActif(NotificationService.Canal.WHATSAPP));
    }

    @GetMapping
    @Operation(summary = "Derniers messages, filtrables par canal, statut et entreprise")
    public List<Ligne> lister(@RequestParam(required = false) String canal,
                              @RequestParam(required = false) String statut,
                              @RequestParam(required = false) Long entrepriseId,
                              @RequestParam(defaultValue = "200") int limite) {
        StringBuilder sql = new StringBuilder("""
                select n.id, n.entreprise_id, e.nom, n.canal, n.destinataire, n.sujet, n.texte, n.motif, n.statut,
                       n.fournisseur, n.tentatives, n.erreur, n.cree_le, n.envoye_le
                from notification_envoi n left join entreprise e on e.id = n.entreprise_id
                where 1 = 1
                """);
        List<Object> parametres = new ArrayList<>();
        if (canal != null && canal.matches("^(EMAIL|SMS|WHATSAPP)$")) {
            sql.append(" and n.canal = ?");
            parametres.add(canal);
        }
        if (statut != null && statut.matches("^(EN_ATTENTE|ENVOYE|SIMULE|ECHEC|ABANDONNE)$")) {
            sql.append(" and n.statut = ?");
            parametres.add(statut);
        }
        if (entrepriseId != null) {
            sql.append(" and n.entreprise_id = ?");
            parametres.add(entrepriseId);
        }
        sql.append(" order by n.id desc limit ?");
        parametres.add(Math.max(1, Math.min(limite, 500)));
        return jdbc.query(sql.toString(), (rs, i) -> new Ligne(rs.getLong(1),
                (Long) rs.getObject(2, Long.class), rs.getString(3), rs.getString(4), rs.getString(5),
                rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getInt(11),
                rs.getString(12), instant(rs.getTimestamp(13)), instant(rs.getTimestamp(14))), parametres.toArray());
    }

    @PostMapping("/{id}/renvoi")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Renvoie un message en echec, abandonne ou simule")
    public void renvoyer(@PathVariable Long id) {
        notifications.renvoyer(id);
    }

    private static Instant instant(java.sql.Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
