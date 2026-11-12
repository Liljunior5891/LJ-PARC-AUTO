package tg.parcauto.api.common.notification;

import jakarta.annotation.PreDestroy;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Envoi des notifications par e-mail, SMS et WhatsApp.
 * <p>
 * Chaque message est d'abord enregistre (table notification_envoi), puis envoye
 * apres la validation de la transaction en cours, sur un fil a part : une panne
 * d'envoi ne fait jamais echouer l'operation qui l'a demandee. Un echec est
 * retente toutes les 5 minutes, 5 fois au plus.
 * <p>
 * Fournisseurs :
 * <ul>
 *   <li>e-mail : serveur SMTP (MAIL_HOST...) s'il est configure ;</li>
 *   <li>SMS et WhatsApp : pas encore de fournisseur branche.</li>
 * </ul>
 * Sans fournisseur, le message est marque SIMULE et ecrit dans les journaux du
 * serveur ; le super-administrateur le lit dans la console (Notifications).
 */
@Slf4j
@Service
public class NotificationService {

    public enum Canal { EMAIL, SMS, WHATSAPP }

    /** Un message prepare (sujet ignore hors e-mail). */
    public record Message(Long entrepriseId, Canal canal, String destinataire, String sujet, String texte,
                          String motif) {
    }

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,255}\\.[^@\\s]{2,}$");
    private static final Pattern TELEPHONE = Pattern.compile("^\\+?[0-9]{8,15}$");
    private static final int TENTATIVES_MAX = 5;

    private final JdbcTemplate jdbc;
    private final ObjectProvider<JavaMailSender> courrier;
    private final String expediteur;
    private final String nomExpediteur;
    private final ThreadPoolExecutor fil = new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(5_000), r -> {
                Thread t = new Thread(r, "notifications");
                t.setDaemon(true);
                return t;
            }, new ThreadPoolExecutor.DiscardPolicy());

    public NotificationService(JdbcTemplate jdbc, ObjectProvider<JavaMailSender> courrier,
                               @Value("${parcauto.notifications.expediteur:no-reply@lj-saas.local}") String expediteur,
                               @Value("${parcauto.notifications.nom-expediteur:LJ SaaS}") String nomExpediteur) {
        this.jdbc = jdbc;
        this.courrier = courrier;
        this.expediteur = expediteur;
        this.nomExpediteur = nomExpediteur;
    }

    /** Vrai si un vrai fournisseur existe pour ce canal (sinon : simulation). */
    public boolean fournisseurActif(Canal canal) {
        return canal == Canal.EMAIL && courrier.getIfAvailable() != null;
    }

    /**
     * Enregistre le message et l'envoie apres validation de la transaction en cours.
     * Un destinataire invalide est ignore (rien n'est enregistre) : renvoie nul.
     */
    public Long envoyer(Message m) {
        String destinataire = normaliser(m.canal(), m.destinataire());
        if (destinataire == null || m.texte() == null || m.texte().isBlank()) {
            return null;
        }
        GeneratedKeyHolder cle = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement("""
                    insert into notification_envoi (entreprise_id, canal, destinataire, sujet, texte, motif)
                    values (?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, m.entrepriseId());
            ps.setString(2, m.canal().name());
            ps.setString(3, destinataire);
            ps.setString(4, tronquer(m.sujet(), 200));
            ps.setString(5, tronquer(m.texte(), 4_000));
            ps.setString(6, tronquer(m.motif(), 40));
            return ps;
        }, cle);
        Map<String, Object> cles = cle.getKeys();
        Long id = cles == null ? null : ((Number) cles.get("id")).longValue();
        if (id == null) {
            return null;
        }
        Runnable lancer = () -> fil.execute(() -> traiter(id));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    lancer.run();
                }
            });
        } else {
            lancer.run();
        }
        return id;
    }

    /** Raccourci : e-mail. */
    public Long email(Long entrepriseId, String adresse, String sujet, String texte, String motif) {
        return envoyer(new Message(entrepriseId, Canal.EMAIL, adresse, sujet, texte, motif));
    }

    /** Raccourci : SMS. */
    public Long sms(Long entrepriseId, String telephone, String texte, String motif) {
        return envoyer(new Message(entrepriseId, Canal.SMS, telephone, null, texte, motif));
    }

    /** Envoie un message enregistre ; appele apres validation, et par les nouvelles tentatives. */
    void traiter(Long id) {
        List<Map<String, Object>> lignes = jdbc.queryForList(
                "select canal, destinataire, sujet, texte, tentatives from notification_envoi where id = ?"
                        + " and statut in ('EN_ATTENTE', 'ECHEC')", id);
        if (lignes.isEmpty()) {
            return;
        }
        Map<String, Object> l = lignes.get(0);
        Canal canal = Canal.valueOf((String) l.get("canal"));
        String destinataire = (String) l.get("destinataire");
        String sujet = (String) l.get("sujet");
        String texte = (String) l.get("texte");
        int tentatives = ((Number) l.get("tentatives")).intValue() + 1;
        try {
            String fournisseur = envoyerVraiment(canal, destinataire, sujet, texte);
            String statut = fournisseur == null ? "SIMULE" : "ENVOYE";
            jdbc.update("update notification_envoi set statut = ?, fournisseur = ?, tentatives = ?, erreur = null,"
                    + " envoye_le = now() where id = ?", statut, fournisseur == null ? "simulation" : fournisseur,
                    tentatives, id);
        } catch (RuntimeException | jakarta.mail.MessagingException | java.io.UnsupportedEncodingException e) {
            String erreur = tronquer(e.getClass().getSimpleName() + " : " + e.getMessage(), 300);
            log.warn("Notification {} ({}) non envoyee, tentative {} : {}", id, canal, tentatives, erreur);
            jdbc.update("update notification_envoi set statut = ?, tentatives = ?, erreur = ? where id = ?",
                    tentatives >= TENTATIVES_MAX ? "ABANDONNE" : "ECHEC", tentatives, erreur, id);
        }
    }

    /** @return le fournisseur utilise, ou nul si le message est seulement simule */
    private String envoyerVraiment(Canal canal, String destinataire, String sujet, String texte)
            throws jakarta.mail.MessagingException, java.io.UnsupportedEncodingException {
        if (canal == Canal.EMAIL) {
            JavaMailSender envoi = courrier.getIfAvailable();
            if (envoi != null) {
                MimeMessage mail = envoi.createMimeMessage();
                MimeMessageHelper aide = new MimeMessageHelper(mail, false, StandardCharsets.UTF_8.name());
                aide.setFrom(new InternetAddress(expediteur, nomExpediteur, StandardCharsets.UTF_8.name()));
                aide.setTo(destinataire);
                aide.setSubject(sujet == null ? nomExpediteur : sujet);
                aide.setText(texte, false);
                envoi.send(mail);
                return "smtp";
            }
        }
        // Aucun fournisseur pour ce canal : le message est seulement trace.
        log.info("[notification simulee] {} -> {} : {}", canal, masquer(destinataire), sujet == null ? "" : sujet);
        return null;
    }

    /** Nouvelles tentatives des envois en echec, et des envois restes en attente (redemarrage du serveur). */
    @Scheduled(fixedDelay = 300_000, initialDelay = 90_000)
    public void reprendre() {
        List<Long> ids = jdbc.queryForList("""
                select id from notification_envoi
                where (statut = 'ECHEC' and tentatives < ?)
                   or (statut = 'EN_ATTENTE' and cree_le < ?)
                order by id limit 200
                """, Long.class, TENTATIVES_MAX, Timestamp.from(Instant.now().minusSeconds(120)));
        ids.forEach(id -> fil.execute(() -> traiter(id)));
    }

    /** Renvoie un message (console du super-administrateur). */
    public void renvoyer(Long id) {
        int n = jdbc.update("update notification_envoi set statut = 'EN_ATTENTE', tentatives = 0, erreur = null"
                + " where id = ? and statut in ('ECHEC', 'ABANDONNE', 'SIMULE')", id);
        if (n > 0) {
            fil.execute(() -> traiter(id));
        }
    }

    static String normaliser(Canal canal, String destinataire) {
        if (destinataire == null) {
            return null;
        }
        if (canal == Canal.EMAIL) {
            String e = destinataire.trim();
            return EMAIL.matcher(e).matches() && e.length() <= 160 ? e : null;
        }
        String t = destinataire.replaceAll("[\\s.()-]", "");
        if (t.matches("^[279][0-9]{7}$")) {
            // Numero togolais a 8 chiffres : indicatif ajoute.
            t = "+228" + t;
        }
        return TELEPHONE.matcher(t).matches() ? t : null;
    }

    static String masquer(String destinataire) {
        if (destinataire == null || destinataire.length() < 5) {
            return "***";
        }
        int at = destinataire.indexOf('@');
        return at > 1 ? destinataire.charAt(0) + "***" + destinataire.substring(at)
                : destinataire.substring(0, 4) + "***" + destinataire.substring(destinataire.length() - 2);
    }

    private static String tronquer(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    @PreDestroy
    void arreter() {
        fil.shutdown();
    }
}
