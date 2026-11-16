package tg.parcauto.api.common.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Envoi d'une notification a l'administrateur d'une entreprise, selon ses choix :
 * WhatsApp au numero donne a l'inscription (a defaut, celui d'un administrateur),
 * e-mail a chaque administrateur actif.
 */
@Service
public class AlertesEntreprise {

    /** Choix d'un type : canaux retenus. */
    public record Choix(boolean whatsapp, boolean email) {
    }

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final String urlApplication;

    public AlertesEntreprise(JdbcTemplate jdbc, NotificationService notifications,
                             @Value("${parcauto.notifications.url-application:http://localhost:4300}") String urlApplication) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.urlApplication = urlApplication.replaceAll("/+$", "");
    }

    public String urlApplication() {
        return urlApplication;
    }

    /** Choix de l'entreprise pour chaque type (reglage par defaut quand rien n'est enregistre). */
    public Map<TypeAlerte, Choix> choix(Long entrepriseId) {
        Map<TypeAlerte, Choix> resultat = new EnumMap<>(TypeAlerte.class);
        for (TypeAlerte t : TypeAlerte.values()) {
            resultat.put(t, new Choix(t.isWhatsappParDefaut(), t.isEmailParDefaut()));
        }
        jdbc.query("select type, whatsapp, email from notif_preference where entreprise_id = ?", rs -> {
            try {
                TypeAlerte t = TypeAlerte.valueOf(rs.getString(1));
                resultat.put(t, new Choix(rs.getBoolean(2), rs.getBoolean(3) || t.isEmailObligatoire()));
            } catch (IllegalArgumentException typeRetire) {
                // Type supprime depuis : ignore.
            }
        }, entrepriseId);
        return resultat;
    }

    public Choix choix(Long entrepriseId, TypeAlerte type) {
        return choix(entrepriseId).get(type);
    }

    /** Enregistre le choix de l'administrateur pour un type. */
    public void choisir(Long entrepriseId, TypeAlerte type, Choix choix, String auteur) {
        jdbc.update("""
                insert into notif_preference (entreprise_id, type, whatsapp, email, modifie_le, modifie_par)
                values (?, ?, ?, ?, now(), ?)
                on conflict (entreprise_id, type)
                do update set whatsapp = excluded.whatsapp, email = excluded.email, modifie_le = now(),
                              modifie_par = excluded.modifie_par
                """, entrepriseId, type.name(), choix.whatsapp(), choix.email() || type.isEmailObligatoire(), auteur);
    }

    /** Numero WhatsApp : celui de l'inscription, sinon le telephone d'un administrateur actif. */
    public String numero(Long entrepriseId) {
        List<String> numeros = jdbc.queryForList("""
                select telephone from (
                    select e.telephone, 0 as rang from entreprise e where e.id = ?
                    union all
                    select u.telephone, 1 from utilisateur u join utilisateur_role r on r.utilisateur_id = u.id
                    where u.entreprise_id = ? and u.actif and r.role = 'ADMIN'
                ) t where telephone is not null and trim(telephone) <> '' order by rang limit 1
                """, String.class, entrepriseId, entrepriseId);
        return numeros.isEmpty() ? null : numeros.get(0);
    }

    // ------------------------------------------------- numero confirme par code

    /** Messages WhatsApp par entreprise et par jour, au plus : le numero de la plateforme ne doit pas etre pris pour un robot. */
    static final int PLAFOND_WHATSAPP_JOUR = 60;
    private static final java.security.SecureRandom ALEA = new java.security.SecureRandom();

    /** Numero des notifications s'il est confirme (et toujours celui de l'entreprise), sinon nul. */
    public String numeroVerifie(Long entrepriseId) {
        String actuel = NotificationService.normaliser(NotificationService.Canal.WHATSAPP, numero(entrepriseId));
        if (actuel == null) {
            return null;
        }
        List<String> verifie = jdbc.queryForList(
                "select numero_verifie from notif_verification_whatsapp where entreprise_id = ? and numero_verifie is not null",
                String.class, entrepriseId);
        return !verifie.isEmpty() && actuel.equals(verifie.get(0)) ? actuel : null;
    }

    boolean plafondAtteint(Long entrepriseId) {
        Integer n = jdbc.queryForObject("""
                select count(*) from notification_envoi
                where entreprise_id = ? and canal = 'WHATSAPP' and cree_le >= current_date
                """, Integer.class, entrepriseId);
        return n != null && n >= PLAFOND_WHATSAPP_JOUR;
    }

    /** Envoie un code a 6 chiffres au numero des notifications ; il sera saisi dans Administration, Notifications. */
    public String demanderCode(Long entrepriseId) {
        String numero = NotificationService.normaliser(NotificationService.Canal.WHATSAPP, numero(entrepriseId));
        if (numero == null) {
            throw new tg.parcauto.api.common.BusinessException(
                    "Aucun numero valide : renseignez le telephone de l'entreprise (avec l'indicatif).");
        }
        String code = String.format("%06d", ALEA.nextInt(1_000_000));
        jdbc.update("""
                insert into notif_verification_whatsapp (entreprise_id, numero_en_cours, code_empreinte, code_expire_le, essais)
                values (?, ?, ?, now() + interval '10 minutes', 0)
                on conflict (entreprise_id) do update set numero_en_cours = excluded.numero_en_cours,
                    code_empreinte = excluded.code_empreinte, code_expire_le = excluded.code_expire_le, essais = 0
                """, entrepriseId, numero, empreinte(entrepriseId, numero, code));
        notifications.whatsapp(entrepriseId, numero, "*Code de confirmation Nova : " + code + "*\n"
                + "Saisissez-le dans Administration, Notifications pour recevoir les alertes de votre entreprise "
                + "sur ce numero. Valable 10 minutes.\n\nSi vous n'avez rien demande, ignorez ce message.", "VERIFICATION_NUMERO");
        return numero;
    }

    /** Valide le code saisi ; leve une erreur s'il est faux, expire ou essaye trop souvent. */
    public void confirmerCode(Long entrepriseId, String code, String auteur) {
        List<java.util.Map<String, Object>> lignes = jdbc.queryForList("""
                select numero_en_cours, code_empreinte, code_expire_le > now() as valable, essais
                from notif_verification_whatsapp where entreprise_id = ? and code_empreinte is not null
                """, entrepriseId);
        if (lignes.isEmpty() || !Boolean.TRUE.equals(lignes.get(0).get("valable"))) {
            throw new tg.parcauto.api.common.BusinessException("Code expire ou absent : demandez un nouveau code.");
        }
        java.util.Map<String, Object> l = lignes.get(0);
        if (((Number) l.get("essais")).intValue() >= 5) {
            throw new tg.parcauto.api.common.BusinessException("Trop d'essais : demandez un nouveau code.");
        }
        String numero = (String) l.get("numero_en_cours");
        String saisi = code == null ? "" : code.replaceAll("\\s", "");
        boolean bon = java.security.MessageDigest.isEqual(
                empreinte(entrepriseId, numero, saisi).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                String.valueOf(l.get("code_empreinte")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!bon) {
            jdbc.update("update notif_verification_whatsapp set essais = essais + 1 where entreprise_id = ?", entrepriseId);
            throw new tg.parcauto.api.common.BusinessException("Code incorrect.");
        }
        jdbc.update("""
                update notif_verification_whatsapp set numero_verifie = numero_en_cours, verifie_le = now(), verifie_par = ?,
                    numero_en_cours = null, code_empreinte = null, code_expire_le = null, essais = 0
                where entreprise_id = ?
                """, auteur, entrepriseId);
    }

    private static String empreinte(Long entrepriseId, String numero, String code) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((entrepriseId + ":" + numero + ":" + code).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(h);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<String> emailsAdministrateurs(Long entrepriseId) {
        return jdbc.queryForList("""
                select distinct u.email from utilisateur u join utilisateur_role r on r.utilisateur_id = u.id
                where u.entreprise_id = ? and u.actif and r.role = 'ADMIN'
                """, String.class, entrepriseId);
    }

    /** Modules actifs de l'entreprise. */
    public List<ModuleApplicatif> modules(Long entrepriseId) {
        return jdbc.queryForList("select module from entreprise_module where entreprise_id = ? and actif", String.class,
                entrepriseId).stream().flatMap(m -> {
                    try {
                        return java.util.stream.Stream.of(ModuleApplicatif.valueOf(m));
                    } catch (IllegalArgumentException e) {
                        return java.util.stream.Stream.empty();
                    }
                }).toList();
    }

    /**
     * Envoie la notification sur les canaux choisis.
     *
     * @param lien chemin dans l'application (/pdv/...), ou nul
     * @param motif trace dans notification_envoi ; nul : ALERTE_ suivi du type
     */
    public void envoyer(Long entrepriseId, TypeAlerte type, String sujet, String texte, String lien, String motif) {
        Choix c = choix(entrepriseId, type);
        if (!c.whatsapp() && !c.email()) {
            return;
        }
        String trace = motif != null ? motif : "ALERTE_" + type.name();
        String adresse = lien == null ? null : urlApplication + lien;
        if (c.whatsapp()) {
            // Seul un numero confirme par code recoit des WhatsApp, dans la limite du plafond du jour.
            String numero = numeroVerifie(entrepriseId);
            if (numero != null && !plafondAtteint(entrepriseId)) {
                notifications.whatsapp(entrepriseId, numero, "*" + sujet + "*\n" + texte
                        + (adresse == null ? "" : "\n\n" + adresse) + "\n\n_Nova_", trace);
            }
        }
        if (c.email()) {
            String corps = "Bonjour,\n\n" + texte + "\n" + (adresse == null ? "" : "\n" + adresse + "\n")
                    + "\n-- \nNova\nMessage automatique : merci de ne pas y repondre.\n"
                    + "Choisissez les notifications recues dans Administration, Notifications.\n";
            for (String email : emailsAdministrateurs(entrepriseId)) {
                notifications.email(entrepriseId, email, sujet, corps, trace);
            }
        }
    }

    /**
     * Marque l'evenement comme traite et l'envoie s'il ne l'a jamais ete.
     * Un evenement survenu pendant que le type etait coupe est marque aussi :
     * reactiver le type n'envoie pas l'arriere.
     *
     * @return vrai si l'evenement est nouveau
     */
    public void oublier(Long entrepriseId, String cle) {
        jdbc.update("delete from notif_alerte_envoyee where entreprise_id = ? and cle = ?", entrepriseId,
                cle.length() > 120 ? cle.substring(0, 120) : cle);
    }

    public boolean marquer(Long entrepriseId, String cle) {
        return jdbc.update("insert into notif_alerte_envoyee (entreprise_id, cle) values (?, ?) on conflict do nothing",
                entrepriseId, cle.length() > 120 ? cle.substring(0, 120) : cle) > 0;
    }
}
