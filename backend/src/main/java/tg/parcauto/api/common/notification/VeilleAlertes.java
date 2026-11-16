package tg.parcauto.api.common.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Veille des evenements a signaler aux administrateurs des entreprises.
 * <p>
 * Plutot que d'alourdir chaque operation metier, la veille relit regulierement
 * les tables (toutes entreprises, en JDBC) et retient ce qui est nouveau : chaque
 * evenement porte une cle, marquee une fois envoyee (notif_alerte_envoyee). Les
 * evenements d'un meme type sont regroupes en un seul message par entreprise.
 * Seuls les evenements recents sont lus : la mise en service n'envoie pas
 * l'historique.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VeilleAlertes {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int LIGNES_MAX = 10;

    /** Un evenement : entreprise, cle unique, ligne du message, chemin dans l'application. */
    record Evenement(Long entrepriseId, String cle, String ligne, String lien) {
    }

    private final JdbcTemplate jdbc;
    private final AlertesEntreprise alertes;

    /** Toutes les 2 minutes : ce qui demande une reaction rapide. */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void evenements() {
        executer("compte", this::compte);
        executer("validations", this::validations);
        executer("ecarts de caisse", this::ecartsCaisse);
        executer("ventes hors ligne", this::ventesHorsLigne);
        executer("commandes en ligne", this::commandesEnLigne);
        executer("alertes GPS", this::alertesGps);
        executer("paiements rejetes", this::paiementsRejetes);
    }

    /** Toutes les heures, de 8 h a 19 h : stock sous le seuil. */
    @Scheduled(cron = "0 5 8-19 * * *")
    public void stock() {
        executer("stock", this::alertesStock);
    }

    /** Chaque matin : echeances du parc. */
    @Scheduled(cron = "0 10 8 * * *")
    public void matin() {
        executer("echeances du parc", this::echeancesParc);
    }

    /** Chaque soir a 20 h : resume de la journee. */
    @Scheduled(cron = "0 0 20 * * *")
    public void soir() {
        executer("resume du jour", this::resumeJournalier);
    }

    private void executer(String nom, Runnable traitement) {
        try {
            traitement.run();
        } catch (RuntimeException e) {
            log.warn("Veille des notifications ({}) interrompue : {}", nom, e.getMessage());
        }
    }

    // --- Compte et securite ---

    void compte() {
        // Bienvenue : envoye une fois, peu apres l'inscription.
        jdbc.query("""
                select e.id, e.nom from entreprise e
                where e.statut = 'ACTIVE' and e.cree_le > now() - interval '2 days'
                """, rs -> {
            Long id = rs.getLong(1);
            if (alertes.marquer(id, "bienvenue")) {
                alertes.envoyer(id, TypeAlerte.COMPTE, "Bienvenue sur Nova",
                        "Le compte de " + rs.getString(2) + " est pret. Vous recevrez sur ce numero les notifications"
                                + " choisies dans Administration, Notifications : alertes de stock, validations,"
                                + " abonnement, ecarts de caisse...", "/notifications", "BIENVENUE");
            }
        });
        List<Evenement> evts = new ArrayList<>();
        // Utilisateurs crees apres l'inscription (le premier administrateur est exclu).
        evts.addAll(jdbc.query("""
                select u.entreprise_id, u.id, trim(coalesce(u.prenom, '') || ' ' || u.nom), u.email, u.cree_par,
                       (select string_agg(r.role, ', ' order by r.role) from utilisateur_role r where r.utilisateur_id = u.id)
                from utilisateur u join entreprise e on e.id = u.entreprise_id
                where e.statut = 'ACTIVE' and u.cree_le > now() - interval '2 days'
                  and u.cree_le > e.cree_le + interval '5 minutes'
                """, (rs, i) -> new Evenement(rs.getLong(1), "utilisateur-" + rs.getLong(2),
                "Nouvel utilisateur : " + rs.getString(3) + " (" + rs.getString(4) + ")"
                        + (rs.getString(6) == null ? "" : " · " + rs.getString(6).toLowerCase())
                        + (rs.getString(5) == null ? "" : " · cree par " + rs.getString(5)), "/utilisateurs")));
        // Modules actives ou retires apres l'inscription.
        evts.addAll(jdbc.query("""
                select m.entreprise_id, m.id, m.module, m.actif,
                       extract(epoch from case when m.actif then m.active_le else m.modifie_le end)::bigint
                from entreprise_module m join entreprise e on e.id = m.entreprise_id
                where e.statut = 'ACTIVE'
                  and case when m.actif then m.active_le else m.modifie_le end > now() - interval '2 days'
                  and case when m.actif then m.active_le else m.modifie_le end > e.cree_le + interval '5 minutes'
                """, (rs, i) -> {
            boolean actif = rs.getBoolean(4);
            return new Evenement(rs.getLong(1), "module-" + (actif ? "on-" : "off-") + rs.getLong(2) + "-" + rs.getLong(5),
                    "Module " + libelleModule(rs.getString(3)) + (actif ? " active" : " retire"), "/modules");
        }));
        envoyerGroupes(TypeAlerte.COMPTE, evts, "Changement sur votre compte", "%d changements sur votre compte");
    }

    // --- Validations en attente de l'administrateur ---

    void validations() {
        List<Evenement> evts = new ArrayList<>();
        evts.addAll(jdbc.query("""
                select d.entreprise_id, d.id, d.numero, d.montant, d.libelle, d.auteur_nom
                from pdv_depense d join entreprise e on e.id = d.entreprise_id
                where e.statut = 'ACTIVE' and d.statut = 'ATTENTE_ADMIN'
                  and coalesce(d.visee_le, d.cree_le) > now() - interval '2 days'
                """, (rs, i) -> new Evenement(rs.getLong(1), "val-pdv-depense-" + rs.getLong(2),
                "Depense " + rs.getString(3) + " · " + montant(rs, 4) + " · " + rs.getString(5) + auteur(rs, 6),
                "/pdv/validations-caisse")));
        evts.addAll(jdbc.query("""
                select s.entreprise_id, s.id, s.numero, s.montant, s.motif, s.auteur_nom
                from pdv_sortie_attente s join entreprise e on e.id = s.entreprise_id
                where e.statut = 'ACTIVE' and s.statut = 'ATTENTE_ADMIN'
                  and coalesce(s.visee_le, s.demandee_le) > now() - interval '2 days'
                """, (rs, i) -> new Evenement(rs.getLong(1), "val-pdv-sortie-" + rs.getLong(2),
                "Sortie de caisse " + rs.getString(3) + " · " + montant(rs, 4) + " · " + rs.getString(5) + auteur(rs, 6),
                "/pdv/validations-caisse")));
        evts.addAll(jdbc.query("""
                select d.entreprise_id, d.id, d.numero, d.montant, d.libelle, d.auteur_nom
                from depense d join entreprise e on e.id = d.entreprise_id
                where e.statut = 'ACTIVE' and d.statut = 'ATTENTE_ADMIN' and d.cree_le > now() - interval '2 days'
                """, (rs, i) -> new Evenement(rs.getLong(1), "val-parc-depense-" + rs.getLong(2),
                "Depense du parc " + rs.getString(3) + " · " + montant(rs, 4) + " · " + rs.getString(5) + auteur(rs, 6),
                "/depenses")));
        evts.addAll(jdbc.query("""
                select d.entreprise_id, d.id, d.numero, d.montant, d.libelle, d.auteur_nom, a.nom
                from agc_depense d join entreprise e on e.id = d.entreprise_id join agc_agence a on a.id = d.agence_id
                where e.statut = 'ACTIVE' and d.statut = 'ATTENTE_ADMIN'
                  and coalesce(d.visee_le, d.cree_le) > now() - interval '2 days'
                """, (rs, i) -> new Evenement(rs.getLong(1), "val-agc-depense-" + rs.getLong(2),
                "Depense " + rs.getString(3) + " (" + rs.getString(7) + ") · " + montant(rs, 4) + " · " + rs.getString(5)
                        + auteur(rs, 6), "/agences/depenses")));
        envoyerGroupes(TypeAlerte.VALIDATION, evts, "Validation en attente", "%d validations en attente");
    }

    // --- Ecarts de caisse ---

    void ecartsCaisse() {
        List<Evenement> evts = new ArrayList<>();
        evts.addAll(jdbc.query("""
                select s.entreprise_id, s.id, s.numero, s.ecart, coalesce(c.nom, 'Caisse'), coalesce(s.fermee_par_nom, s.caissier_nom)
                from pdv_session_caisse s join entreprise e on e.id = s.entreprise_id
                left join pdv_caisse c on c.id = s.caisse_id
                where e.statut = 'ACTIVE' and s.statut = 'FERMEE' and s.ecart <> 0
                  and s.fermee_le > now() - interval '2 days'
                """, (rs, i) -> new Evenement(rs.getLong(1), "ecart-pdv-" + rs.getLong(2),
                rs.getString(5) + " · " + rs.getString(3) + " · " + ecart(rs.getBigDecimal(4)) + auteur(rs, 6),
                "/pdv/caisses")));
        evts.addAll(jdbc.query("""
                select s.entreprise_id, s.id, s.numero, s.ecart, a.nom, coalesce(s.fermee_par_nom, s.caissier_nom)
                from agc_session_caisse s join entreprise e on e.id = s.entreprise_id
                join agc_agence a on a.id = s.agence_id
                where e.statut = 'ACTIVE' and s.statut = 'FERMEE' and s.ecart <> 0
                  and s.fermee_le > now() - interval '2 days'
                """, (rs, i) -> new Evenement(rs.getLong(1), "ecart-agc-" + rs.getLong(2),
                "Agence " + rs.getString(5) + " · " + rs.getString(3) + " · " + ecart(rs.getBigDecimal(4)) + auteur(rs, 6),
                "/agences/caisses")));
        envoyerGroupes(TypeAlerte.ECART_CAISSE, evts, "Caisse fermee avec un ecart", "%d caisses fermees avec un ecart");
    }

    // --- Ventes hors ligne en conflit ---

    void ventesHorsLigne() {
        List<Evenement> evts = jdbc.query("""
                select v.entreprise_id, v.id, v.numero_provisoire, v.total, v.caissier_nom, v.message
                from pdv_vente_hors_ligne v join entreprise e on e.id = v.entreprise_id
                where e.statut = 'ACTIVE' and v.statut = 'CONFLIT' and v.cree_le > now() - interval '2 days'
                """, (rs, i) -> new Evenement(rs.getLong(1), "hors-ligne-" + rs.getLong(2),
                rs.getString(3) + " · " + montant(rs, 4) + auteur(rs, 5)
                        + (rs.getString(6) == null ? "" : " · " + rs.getString(6)), "/pdv/hors-ligne"));
        envoyerGroupes(TypeAlerte.VENTE_HORS_LIGNE, evts, "Vente hors ligne a traiter", "%d ventes hors ligne a traiter");
    }

    // --- Boutique en ligne ---

    void commandesEnLigne() {
        List<Evenement> evts = new ArrayList<>();
        evts.addAll(jdbc.query("""
                select c.entreprise_id, c.id, c.numero, c.client_nom, c.total, c.mode_remise
                from mkt_commande c join entreprise e on e.id = c.entreprise_id
                where e.statut = 'ACTIVE' and c.statut = 'NOUVELLE' and c.date_commande > now() - interval '1 day'
                """, (rs, i) -> new Evenement(rs.getLong(1), "commande-" + rs.getLong(2),
                "Commande " + rs.getString(3) + " · " + saisiePublique(rs.getString(4)) + " · " + montant(rs, 5)
                        + (rs.getString(6) == null ? "" : " · " + rs.getString(6).toLowerCase()), "/marketplace/commandes")));
        evts.addAll(jdbc.query("""
                select d.entreprise_id, d.id, d.numero, d.client_nom, d.date_debut, d.date_fin
                from mkt_demande_location d join entreprise e on e.id = d.entreprise_id
                where e.statut = 'ACTIVE' and d.statut = 'NOUVELLE' and d.date_demande > now() - interval '1 day'
                """, (rs, i) -> new Evenement(rs.getLong(1), "location-" + rs.getLong(2),
                "Demande de location " + rs.getString(3) + " · " + saisiePublique(rs.getString(4)) + " · du "
                        + jour(rs.getDate(5)) + " au " + jour(rs.getDate(6)), "/marketplace/locations")));
        envoyerGroupes(TypeAlerte.COMMANDE_EN_LIGNE, evts, "Nouvelle commande en ligne", "%d nouvelles demandes en ligne");
    }

    // --- GPS ---

    void alertesGps() {
        List<Evenement> evts = jdbc.query("""
                select a.entreprise_id, a.id, coalesce(c.immatriculation, 'Vehicule'), a.message
                from alerte_gps a join entreprise e on e.id = a.entreprise_id left join camion c on c.id = a.camion_id
                where e.statut = 'ACTIVE' and a.gravite = 'CRITIQUE' and not a.traitee
                  and a.horodatage > now() - interval '1 hour'
                """, (rs, i) -> new Evenement(rs.getLong(1), "gps-" + rs.getLong(2),
                rs.getString(3) + " : " + rs.getString(4), "/gps"));
        envoyerGroupes(TypeAlerte.ALERTE_GPS, evts, "Alerte GPS critique", "%d alertes GPS critiques");
    }

    // --- Abonnement : paiement rejete par la plateforme (le recu d'un paiement valide part a la validation).
    // Un paiement PayGate abandonne, expire ou refuse est rejete tout seul : l'administrateur l'a vu a l'ecran. ---

    void paiementsRejetes() {
        List<Evenement> evts = jdbc.query("""
                select p.entreprise_id, p.id, p.montant, p.devise, p.motif_rejet
                from abo_paiement p join entreprise e on e.id = p.entreprise_id
                where p.statut = 'REJETE' and p.traite_le > now() - interval '2 days'
                  and coalesce(p.traite_par, '') <> 'PayGate'
                """, (rs, i) -> new Evenement(rs.getLong(1), "paiement-rejete-" + rs.getLong(2),
                "Paiement de " + entier(rs.getBigDecimal(3)) + " " + rs.getString(4) + " rejete : "
                        + Objects.toString(rs.getString(5), "motif non precise"), "/abonnement"));
        envoyerGroupes(TypeAlerte.ABONNEMENT, evts, "Paiement d'abonnement rejete", "%d paiements d'abonnement rejetes");
    }

    // --- Stock ---

    void alertesStock() {
        List<Evenement> evts = jdbc.query("""
                select a.entreprise_id, a.id, a.nom, a.stock_actuel - a.stock_reserve, a.seuil_alerte, a.unite
                from pdv_article a join entreprise e on e.id = a.entreprise_id
                join entreprise_module m on m.entreprise_id = a.entreprise_id and m.module = 'POINT_DE_VENTE' and m.actif
                where e.statut = 'ACTIVE' and a.actif and a.suivi_stock and a.seuil_alerte is not null
                  and a.stock_actuel - a.stock_reserve <= a.seuil_alerte
                order by a.stock_actuel - a.stock_reserve, a.nom
                """, (rs, i) -> {
            BigDecimal reste = rs.getBigDecimal(4);
            String unite = rs.getString(6) == null ? "" : " " + rs.getString(6).toLowerCase();
            String ligne = reste.signum() <= 0 ? "Rupture : " + rs.getString(3)
                    : rs.getString(3) + " : reste " + quantite(reste) + unite + " (seuil " + quantite(rs.getBigDecimal(5)) + ")";
            return new Evenement(rs.getLong(1), "stock-" + rs.getLong(2) + "-" + LocalDate.now(), ligne, "/pdv/stock");
        });
        envoyerGroupes(TypeAlerte.ALERTE_STOCK, evts, "Alerte de stock", "%d articles sous le seuil d'alerte");
    }

    // --- Echeances du parc ---

    void echeancesParc() {
        List<Evenement> evts = jdbc.query("""
                select d.entreprise_id, d.id, d.libelle, d.numero, d.date_expiration,
                       case when d.proprietaire_type = 'CAMION' then (select c.immatriculation from camion c where c.id = d.proprietaire_id)
                            when d.proprietaire_type = 'CHAUFFEUR' then (select trim(coalesce(ch.prenom, '') || ' ' || ch.nom) from chauffeur ch where ch.id = d.proprietaire_id)
                       end
                from document d join entreprise e on e.id = d.entreprise_id
                join entreprise_module m on m.entreprise_id = d.entreprise_id and m.module = 'GESTION_PARC' and m.actif
                where e.statut = 'ACTIVE' and not d.archive and d.proprietaire_type in ('CAMION', 'CHAUFFEUR')
                  and d.date_expiration between current_date - 30 and current_date + 7
                order by d.date_expiration
                """, (rs, i) -> {
            LocalDate fin = rs.getDate(5).toLocalDate();
            String qui = rs.getString(6) == null ? "" : " (" + rs.getString(6) + ")";
            return new Evenement(rs.getLong(1), "document-" + rs.getLong(2) + "-" + fin,
                    rs.getString(3) + qui + (fin.isBefore(LocalDate.now()) ? " · expire depuis le " : " · expire le ")
                            + JOUR.format(fin), "/documents");
        });
        envoyerGroupes(TypeAlerte.ECHEANCES_PARC, evts, "Document a renouveler", "%d documents a renouveler");
    }

    // --- Resume du jour ---

    void resumeJournalier() {
        LocalDate aujourdhui = LocalDate.now();
        List<Long> entreprises = jdbc.queryForList("""
                select e.id from entreprise e
                join entreprise_module m on m.entreprise_id = e.id and m.module = 'POINT_DE_VENTE' and m.actif
                where e.statut = 'ACTIVE'
                """, Long.class);
        for (Long id : entreprises) {
            AlertesEntreprise.Choix c = alertes.choix(id, TypeAlerte.RESUME_JOURNALIER);
            if ((!c.whatsapp() && !c.email()) || !alertes.marquer(id, "resume-" + aujourdhui)) {
                continue;
            }
            Map<String, Object> v = jdbc.queryForMap("""
                    select count(*) as nombre, coalesce(sum(total), 0) as total from pdv_vente
                    where entreprise_id = ? and statut = 'VALIDEE' and date_vente >= current_date
                    """, id);
            BigDecimal depenses = jdbc.queryForObject("""
                    select coalesce(sum(montant), 0) from pdv_depense
                    where entreprise_id = ? and statut = 'VALIDE' and date_depense = current_date
                    """, BigDecimal.class, id);
            long nombre = ((Number) v.get("nombre")).longValue();
            String texte = "Journee du " + JOUR.format(aujourdhui) + "\n"
                    + "Ventes : " + nombre + " pour " + entier((BigDecimal) v.get("total")) + " F\n"
                    + "Depenses : " + entier(depenses) + " F";
            alertes.envoyer(id, TypeAlerte.RESUME_JOURNALIER, "Resume de la journee", texte, "/pdv/tableau-bord", null);
        }
    }

    // --- Regroupement et envoi ---

    /** Garde les evenements nouveaux et envoie un message par entreprise. */
    void envoyerGroupes(TypeAlerte type, List<Evenement> evenements, String sujetUn, String sujetPlusieurs) {
        Map<Long, List<Evenement>> parEntreprise = new LinkedHashMap<>();
        for (Evenement e : evenements) {
            if (alertes.marquer(e.entrepriseId(), e.cle())) {
                parEntreprise.computeIfAbsent(e.entrepriseId(), k -> new ArrayList<>()).add(e);
            }
        }
        parEntreprise.forEach((entreprise, liste) -> {
            String sujet = liste.size() == 1 ? sujetUn : sujetPlusieurs.formatted(liste.size());
            try {
                alertes.envoyer(entreprise, type, sujet, texte(liste), lienCommun(liste), null);
            } catch (RuntimeException e) {
                // Rien n'est parti : les evenements seront repris au passage suivant.
                liste.forEach(ev -> alertes.oublier(entreprise, ev.cle()));
                log.warn("Notification {} non preparee pour l'entreprise {} : {}", type, entreprise, e.getMessage());
            }
        });
    }

    /**
     * Texte saisi par un visiteur de la boutique en ligne (nom du client), repris dans un message
     * a l'administrateur : sans lien, sans retour a la ligne ni mise en forme WhatsApp, et court.
     * Il ne peut pas se faire passer pour un message de Nova.
     */
    static String saisiePublique(String brut) {
        if (brut == null) {
            return "client";
        }
        String t = brut.replaceAll("(?i)\\b(?:https?://|www\\.)\\S*", " ")
                .replaceAll("(?i)\\b[\\w-]+(?:\\.[\\w-]+)*\\.(?:com|net|org|tg|fr|io|me|ly|co|info|xyz|app|link)\\b\\S*", " ")
                .replaceAll("[*_~`>\\[\\]]", " ")
                .replaceAll("\\s+", " ").trim();
        if (t.length() > 40) {
            t = t.substring(0, 40).trim() + "...";
        }
        return t.isEmpty() ? "client" : "« " + t + " »";
    }

    static String texte(List<Evenement> liste) {
        StringBuilder sb = new StringBuilder();
        liste.stream().limit(LIGNES_MAX).forEach(e -> sb.append(sb.isEmpty() ? "" : "\n").append("• ").append(e.ligne()));
        if (liste.size() > LIGNES_MAX) {
            sb.append("\n... et ").append(liste.size() - LIGNES_MAX).append(" autre(s)");
        }
        return sb.toString();
    }

    static String lienCommun(List<Evenement> liste) {
        String lien = liste.get(0).lien();
        return liste.stream().allMatch(e -> Objects.equals(e.lien(), lien)) ? lien : null;
    }

    private static String libelleModule(String code) {
        try {
            return ModuleApplicatif.valueOf(code).getLibelle();
        } catch (IllegalArgumentException e) {
            return code;
        }
    }

    private static String auteur(ResultSet rs, int colonne) throws SQLException {
        String nom = rs.getString(colonne);
        return nom == null || nom.isBlank() ? "" : " · " + nom;
    }

    private static String montant(ResultSet rs, int colonne) throws SQLException {
        return entier(rs.getBigDecimal(colonne)) + " F";
    }

    static String ecart(BigDecimal ecart) {
        return (ecart.signum() < 0 ? "manquant de " : "excedent de ") + entier(ecart.abs()) + " F";
    }

    static String entier(BigDecimal montant) {
        if (montant == null) {
            return "0";
        }
        return String.format(java.util.Locale.ROOT, "%,d", montant.setScale(0, RoundingMode.HALF_UP).longValue()).replace(',', ' ');
    }

    private static String quantite(BigDecimal q) {
        return q == null ? "?" : q.stripTrailingZeros().toPlainString();
    }

    private static String jour(java.sql.Date d) {
        return d == null ? "?" : JOUR.format(d.toLocalDate());
    }
}
