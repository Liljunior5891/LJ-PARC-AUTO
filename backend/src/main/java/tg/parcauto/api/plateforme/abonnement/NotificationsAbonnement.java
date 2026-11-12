package tg.parcauto.api.plateforme.abonnement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tg.parcauto.api.common.notification.NotificationService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Messages de l'abonnement envoyes aux administrateurs de l'entreprise :
 * rappels avant l'echeance (et avant la fin de l'essai), passage en grace puis
 * en lecture seule, et recu de chaque paiement valide. E-mail aux
 * administrateurs, SMS au telephone de l'entreprise.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationsAbonnement {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    @Value("${parcauto.notifications.url-application:http://localhost:4300}")
    private String urlApplication;

    /** Chaque matin a 8 h : rappels du jour, une seule fois par entreprise et par echeance. */
    @Scheduled(cron = "0 0 8 * * *")
    public void rappels() {
        Map<String, Object> p = jdbc.queryForMap("select jours_grace, jours_rappel from abo_parametres order by id limit 1");
        int grace = ((Number) p.get("jours_grace")).intValue();
        int rappel = Math.max(1, ((Number) p.get("jours_rappel")).intValue());
        LocalDate aujourdhui = LocalDate.now();
        List<Map<String, Object>> abonnements = jdbc.queryForList("""
                select a.entreprise_id, a.essai, a.fin, e.nom, e.telephone
                from abo_abonnement a join entreprise e on e.id = a.entreprise_id
                where e.statut = 'ACTIVE'
                """);
        for (Map<String, Object> a : abonnements) {
            try {
                rappel(a, aujourdhui, rappel, grace);
            } catch (RuntimeException ex) {
                log.warn("Rappel d'abonnement non prepare pour l'entreprise {} : {}", a.get("entreprise_id"), ex.getMessage());
            }
        }
    }

    private void rappel(Map<String, Object> a, LocalDate aujourdhui, int joursRappel, int joursGrace) {
        Long entreprise = ((Number) a.get("entreprise_id")).longValue();
        boolean essai = Boolean.TRUE.equals(a.get("essai"));
        LocalDate fin = ((java.sql.Date) a.get("fin")).toLocalDate();
        String nom = (String) a.get("nom");
        long reste = ChronoUnit.DAYS.between(aujourdhui, fin);
        LocalDate lectureSeule = fin.plusDays(joursGrace + 1L);
        String cle;
        String sujet;
        String texte;
        if (essai && (reste == 3 || reste == 0)) {
            cle = "ESSAI_J" + reste;
            sujet = reste == 0 ? "Votre essai gratuit se termine aujourd'hui" : "Votre essai gratuit se termine dans 3 jours";
            texte = "Bonjour,\n\nL'essai gratuit de " + nom + " se termine le " + JOUR.format(fin) + ".\n"
                    + "Choisissez votre formule dans Mon abonnement pour continuer sans interruption :\n"
                    + urlApplication + "/abonnement\n";
        } else if (!essai && (reste == joursRappel || reste == 1)) {
            cle = "ECHEANCE_J" + reste;
            sujet = "Votre abonnement arrive a echeance le " + JOUR.format(fin);
            texte = "Bonjour,\n\nL'abonnement de " + nom + " arrive a echeance le " + JOUR.format(fin)
                    + " (dans " + reste + " jour" + (reste > 1 ? "s" : "") + ").\n"
                    + "Renouvelez-le depuis Mon abonnement :\n" + urlApplication + "/abonnement\n";
        } else if (reste == -1) {
            cle = "GRACE";
            sujet = "Echeance depassee : votre compte passera en lecture seule le " + JOUR.format(lectureSeule);
            texte = "Bonjour,\n\nL'abonnement de " + nom + " a pris fin le " + JOUR.format(fin) + ".\n"
                    + "Tout fonctionne encore jusqu'au " + JOUR.format(lectureSeule.minusDays(1))
                    + ", puis le compte passera en lecture seule.\n"
                    + "Renouvelez-le depuis Mon abonnement :\n" + urlApplication + "/abonnement\n";
        } else if (aujourdhui.equals(lectureSeule)) {
            cle = "LECTURE_SEULE";
            sujet = "Votre compte est en lecture seule";
            texte = "Bonjour,\n\nL'abonnement de " + nom + " a expire : le compte est desormais en lecture seule.\n"
                    + "Vos donnees sont conservees. Renouvelez l'abonnement pour reprendre la saisie :\n"
                    + urlApplication + "/abonnement\n";
        } else {
            return;
        }
        // Une seule fois par echeance : la date de fin fait partie de la cle.
        String motif = cle + "_" + fin;
        Integer deja = jdbc.queryForObject("select count(*) from notification_envoi where entreprise_id = ? and motif = ?",
                Integer.class, entreprise, motif);
        if (deja != null && deja > 0) {
            return;
        }
        for (String email : administrateurs(entreprise)) {
            notifications.email(entreprise, email, sujet, texte + signature(), motif);
        }
        String telephone = (String) a.get("telephone");
        if (telephone != null && !telephone.isBlank()) {
            notifications.sms(entreprise, telephone, "LJ SaaS : " + sujet + ". " + urlApplication + "/abonnement", motif);
        }
    }

    /** Recu envoye aux administrateurs quand un paiement d'abonnement est valide. */
    public void recu(PaiementAbonnement p, String nomEntreprise) {
        String texte = "Bonjour,\n\nNous avons bien recu le paiement de " + nomEntreprise + ".\n\n"
                + "Facture : " + p.getNumeroFacture() + "\n"
                + "Montant : " + montant(p.getMontant()) + " " + p.getDevise() + " (" + p.getMoyen().getLibelle() + ")\n"
                + "Formule : " + p.getFormuleNom() + ", " + p.libelleDuree() + "\n"
                + "Periode : du " + JOUR.format(p.getPeriodeDebut()) + " au " + JOUR.format(p.getPeriodeFin()) + "\n\n"
                + "La facture est disponible dans Mon abonnement :\n" + urlApplication + "/abonnement\n";
        for (String email : administrateurs(p.getEntrepriseId())) {
            notifications.email(p.getEntrepriseId(), email, "Recu de paiement " + p.getNumeroFacture(),
                    texte + signature(), "RECU_PAIEMENT");
        }
    }

    private List<String> administrateurs(Long entreprise) {
        return jdbc.queryForList("""
                select u.email from utilisateur u join utilisateur_role r on r.utilisateur_id = u.id
                where u.entreprise_id = ? and u.actif and r.role = 'ADMIN'
                """, String.class, entreprise);
    }

    private static String montant(BigDecimal m) {
        return String.format("%,d", m.longValue()).replace(',', ' ');
    }

    private static String signature() {
        return "\n-- \nLJ SaaS\nMessage automatique : merci de ne pas y repondre.\n";
    }
}
