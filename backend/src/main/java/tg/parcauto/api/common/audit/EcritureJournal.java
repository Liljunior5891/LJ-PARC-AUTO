package tg.parcauto.api.common.audit;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Ecrit les lignes du journal d'audit, hors de la transaction metier : en JDBC
 * direct, sur un fil dedie. Une panne du journal ne fait jamais echouer
 * l'operation tracee (elle est deja validee) ; elle est seulement consignee
 * dans les logs.
 */
@Slf4j
@Component
public class EcritureJournal {

    /** Une ligne a ecrire ; l'auteur et l'adresse sont lus sur le fil de la requete. */
    public record Evenement(Long entrepriseId, Instant date, Long auteurId, String auteurNom, String module, String action,
                            String objet, String objetLibelle, Long objetId, String description, String avant, String apres,
                            String adresseIp) {
    }

    /** Auteur et adresse de la requete en cours. */
    public record Contexte(Long auteurId, String auteurNom, String adresseIp) {
    }

    private static final String INSERTION = """
            insert into journal_activite (entreprise_id, date_action, auteur_id, auteur_nom, module, action, objet,
                                          objet_libelle, objet_id, description, avant, apres, adresse_ip, cree_le, cree_par, version)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;

    private final JdbcTemplate jdbc;
    private final ExecutorService fil = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "journal-audit");
        t.setDaemon(true);
        return t;
    });

    public EcritureJournal(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void ecrire(Evenement e) {
        // Une seule regle pour les lignes sans entreprise : ce sont des actions de la plateforme
        // (journal general du super-admin), sauf les connexions qui gardent leur module.
        Evenement ligne = e.entrepriseId() == null && !"CONNEXION".equals(e.module())
                ? new Evenement(null, e.date(), e.auteurId(), e.auteurNom(), "PLATEFORME", e.action(), e.objet(),
                        e.objetLibelle(), e.objetId(), e.description(), e.avant(), e.apres(), e.adresseIp())
                : e;
        fil.submit(() -> ecrireMaintenant(ligne));
    }

    /** Ecrit apres la validation de la transaction en cours, s'il y en a une : rien n'est trace pour une operation annulee. */
    public void ecrireApresValidation(Evenement e) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            ecrire(e);
                        }
                    });
        } else {
            ecrire(e);
        }
    }

    private void ecrireMaintenant(Evenement e) {
        try {
                jdbc.update(INSERTION, e.entrepriseId(), Timestamp.from(e.date()), e.auteurId(), tronquer(e.auteurNom(), 150),
                        e.module(), e.action(), tronquer(e.objet(), 60), tronquer(e.objetLibelle(), 80), e.objetId(),
                        tronquer(e.description(), 500), e.avant(), e.apres(), tronquer(e.adresseIp(), 60),
                        Timestamp.from(e.date()), tronquer(e.auteurNom(), 100));
        } catch (RuntimeException ex) {
            log.warn("Journal d'audit non ecrit ({} {} #{}) : {}", e.action(), e.objet(), e.objetId(), ex.getMessage());
        }
    }

    /** Auteur connecte et adresse IP de la requete ; « Systeme » hors requete, « Visiteur » sans connexion. */
    public static Contexte contexte() {
        String ip = null;
        boolean requete = false;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributs) {
            requete = true;
            HttpServletRequest r = attributs.getRequest();
            String transmise = r.getHeader("X-Forwarded-For");
            ip = transmise != null && !transmise.isBlank() ? transmise.split(",")[0].trim() : r.getRemoteAddr();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UtilisateurPrincipal p) {
            return new Contexte(p.getId(), p.getNomComplet(), ip);
        }
        return new Contexte(null, requete ? "Visiteur (en ligne)" : "Systeme", ip);
    }

    private static String tronquer(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    @PreDestroy
    void arreter() throws InterruptedException {
        fil.shutdown();
        fil.awaitTermination(5, TimeUnit.SECONDS);
    }
}
