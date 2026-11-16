package tg.parcauto.api.plateforme.abonnement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tg.parcauto.api.common.notification.AlertesEntreprise;
import tg.parcauto.api.common.notification.TypeAlerte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Messages de l'abonnement envoyes aux administrateurs de l'entreprise :
 * rappels avant l'echeance (et avant la fin de l'essai), passage en grace puis
 * en lecture seule, et recu de chaque paiement valide. WhatsApp au numero de
 * l'inscription et e-mail aux administrateurs, selon les choix de l'entreprise
 * (type ABONNEMENT).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationsAbonnement {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JdbcTemplate jdbc;
    private final AlertesEntreprise alertes;

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
            texte = "L'essai gratuit de " + nom + " se termine le " + JOUR.format(fin) + ".\n"
                    + "Choisissez votre formule dans Mon abonnement pour continuer sans interruption.";
        } else if (!essai && (reste == joursRappel || reste == 1)) {
            cle = "ECHEANCE_J" + reste;
            sujet = "Votre abonnement arrive a echeance le " + JOUR.format(fin);
            texte = "L'abonnement de " + nom + " arrive a echeance le " + JOUR.format(fin)
                    + " (dans " + reste + " jour" + (reste > 1 ? "s" : "") + ").\n"
                    + "Renouvelez-le depuis Mon abonnement.";
        } else if (reste == -1) {
            cle = "GRACE";
            sujet = "Echeance depassee : votre compte passera en lecture seule le " + JOUR.format(lectureSeule);
            texte = "L'abonnement de " + nom + " a pris fin le " + JOUR.format(fin) + ".\n"
                    + "Tout fonctionne encore jusqu'au " + JOUR.format(lectureSeule.minusDays(1))
                    + ", puis le compte passera en lecture seule.\n"
                    + "Renouvelez-le depuis Mon abonnement.";
        } else if (aujourdhui.equals(lectureSeule)) {
            cle = "LECTURE_SEULE";
            sujet = "Abonnement epuise : votre compte est en lecture seule";
            texte = "L'abonnement de " + nom + " est epuise : le compte est desormais en lecture seule.\n"
                    + "Vos donnees sont conservees. Renouvelez l'abonnement pour reprendre la saisie.";
        } else {
            return;
        }
        // Une seule fois par echeance : la date de fin fait partie de la cle.
        String motif = cle + "_" + fin;
        if (!alertes.marquer(entreprise, motif)) {
            return;
        }
        try {
            alertes.envoyer(entreprise, TypeAlerte.ABONNEMENT, sujet, texte, "/abonnement", motif);
        } catch (RuntimeException e) {
            // Rien n'est parti : la marque est retiree pour que le rappel soit retente.
            alertes.oublier(entreprise, motif);
            throw e;
        }
    }

    /** Recu envoye aux administrateurs quand un paiement d'abonnement est valide. */
    public void recu(PaiementAbonnement p, String nomEntreprise) {
        String texte = "Nous avons bien recu le paiement de " + nomEntreprise + ".\n"
                + "Facture : " + p.getNumeroFacture() + "\n"
                + "Montant : " + montant(p.getMontant()) + " " + p.getDevise() + " (" + p.getMoyen().getLibelle() + ")\n"
                + "Formule : " + p.getFormuleNom() + ", " + p.libelleDuree() + "\n"
                + "Periode : du " + JOUR.format(p.getPeriodeDebut()) + " au " + JOUR.format(p.getPeriodeFin()) + "\n"
                + "La facture est disponible dans Mon abonnement.";
        alertes.envoyer(p.getEntrepriseId(), TypeAlerte.ABONNEMENT, "Recu de paiement " + p.getNumeroFacture(), texte,
                "/abonnement", "RECU_PAIEMENT");
    }

    private static String montant(BigDecimal m) {
        return String.format(java.util.Locale.ROOT, "%,d", m.longValue()).replace(',', ' ');
    }
}
