package tg.parcauto.api.plateforme.entreprise;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.plateforme.abonnement.AbonnementService;
import tg.parcauto.api.plateforme.abonnement.AbonnementService.AbonnementVue;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tableau de bord de l'exploitant : croissance, revenus d'abonnement, usage et
 * echeances. Les volumes d'activite sont des comptages globaux : aucun montant
 * ni aucune donnee metier d'une entreprise n'en sort.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TableauBordPlateformeService {

    private static final int MOIS_SERIE = 12;

    private final EntrepriseRepository entreprises;
    private final AbonnementService abonnements;
    private final JdbcTemplate jdbc;

    public record Point(String cle, String libelle, BigDecimal valeur) {
    }

    public record Activite(String code, String libelle, long total, long ceMois) {
    }

    public record Echeance(Long entrepriseId, String nom, String etat, String etatLibelle, LocalDate fin,
                           long joursRestants, String formuleNom, boolean essai, BigDecimal mensualite,
                           boolean paiementEnAttente) {
    }

    public record EntrepriseCourte(Long id, String nom, String pays, java.time.Instant creeLe, long utilisateurs,
                                   String etatLibelle) {
    }

    public record TableauBord(
            String devise,
            long entreprises, long actives, long suspendues,
            long enEssai, long payantes, long aRenouveler, long enGrace, long expirees,
            long inscritesCeMois, long inscritesMoisPrecedent,
            long utilisateurs, long utilisateursActifs, long connectes7j, long entreprisesActives30j,
            BigDecimal revenuMensuelRecurrent, BigDecimal revenuAnnuelRecurrent,
            BigDecimal encaisseCeMois, BigDecimal encaisseMoisPrecedent, BigDecimal encaisseAnnee,
            BigDecimal encaisseTotal, BigDecimal panierMoyen,
            long paiementsEnAttente, BigDecimal montantEnAttente,
            /** Part des entreprises ayant paye au moins une fois, en pourcentage. */
            BigDecimal tauxConversion,
            List<Point> inscriptionsParMois, List<Point> revenusParMois,
            List<Point> parEtat, List<Point> parFormule, List<Point> parModule, List<Point> parPays,
            List<Point> parTypeActivite, List<Point> parMoyen,
            List<Activite> activite,
            List<Echeance> echeances,
            List<EntrepriseCourte> dernieresInscrites, List<EntrepriseCourte> plusGrandes) {
    }

    public TableauBord tableauBord() {
        List<Entreprise> liste = entreprises.findAll();
        Map<Long, Entreprise> parId = liste.stream().collect(Collectors.toMap(Entreprise::getId, Function.identity()));
        List<AbonnementVue> abos = abonnements.tousLesAbonnements();
        Map<Long, AbonnementVue> aboParId = abos.stream()
                .collect(Collectors.toMap(AbonnementVue::entrepriseId, Function.identity()));
        String devise = abonnements.parametres().getDevise();

        ZoneId zone = ZoneId.systemDefault();
        YearMonth moisCourant = YearMonth.now();
        YearMonth moisPrecedent = moisCourant.minusMonths(1);
        Map<YearMonth, Long> inscriptions = liste.stream().filter(e -> e.getCreeLe() != null)
                .collect(Collectors.groupingBy(e -> YearMonth.from(e.getCreeLe().atZone(zone)), Collectors.counting()));

        long actives = liste.stream().filter(Entreprise::active).count();
        Map<String, Long> etats = abos.stream()
                .collect(Collectors.groupingBy(AbonnementVue::etat, Collectors.counting()));

        // Revenu recurrent : abonnements payes encore utilisables (actifs, a renouveler ou en grace).
        BigDecimal mrr = abos.stream()
                .filter(a -> !a.essai() && !a.lectureSeule())
                .filter(a -> parId.containsKey(a.entrepriseId()) && parId.get(a.entrepriseId()).active())
                .map(AbonnementVue::mensualite).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Paiements valides, par mois de validation.
        Map<YearMonth, BigDecimal> revenus = new LinkedHashMap<>();
        Map<String, BigDecimal> parMoyen = new LinkedHashMap<>();
        BigDecimal[] total = {BigDecimal.ZERO};
        long[] nombrePayes = {0};
        jdbc.query("SELECT traite_le, montant, moyen FROM abo_paiement WHERE statut = 'VALIDE'", rs -> {
            BigDecimal montant = rs.getBigDecimal(2);
            java.sql.Timestamp t = rs.getTimestamp(1);
            if (t != null) {
                revenus.merge(YearMonth.from(t.toInstant().atZone(zone)), montant, BigDecimal::add);
            }
            parMoyen.merge(rs.getString(3), montant, BigDecimal::add);
            total[0] = total[0].add(montant);
            nombrePayes[0]++;
        });
        BigDecimal encaisseAnnee = revenus.entrySet().stream()
                .filter(e -> e.getKey().getYear() == moisCourant.getYear())
                .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        long payeursDistincts = valeur("SELECT count(DISTINCT entreprise_id) FROM abo_paiement WHERE statut = 'VALIDE'");
        long enAttente = valeur("SELECT count(*) FROM abo_paiement WHERE statut = 'EN_ATTENTE'");
        BigDecimal montantEnAttente = jdbc.queryForObject(
                "SELECT coalesce(sum(montant), 0) FROM abo_paiement WHERE statut = 'EN_ATTENTE'", BigDecimal.class);

        List<Point> serieInscriptions = new ArrayList<>();
        List<Point> serieRevenus = new ArrayList<>();
        for (int i = MOIS_SERIE - 1; i >= 0; i--) {
            YearMonth m = moisCourant.minusMonths(i);
            serieInscriptions.add(new Point(m.toString(), libelleMois(m), BigDecimal.valueOf(inscriptions.getOrDefault(m, 0L))));
            serieRevenus.add(new Point(m.toString(), libelleMois(m), revenus.getOrDefault(m, BigDecimal.ZERO)));
        }

        List<Point> parEtat = new ArrayList<>();
        for (var e : tg.parcauto.api.plateforme.abonnement.EtatAbonnement.values()) {
            parEtat.add(new Point(e.name(), e.getLibelle(), BigDecimal.valueOf(etats.getOrDefault(e.name(), 0L))));
        }

        Map<String, Long> formules = abos.stream()
                .collect(Collectors.groupingBy(a -> a.essai() || a.formuleNom() == null ? "Essai gratuit" : a.formuleNom(),
                        LinkedHashMap::new, Collectors.counting()));

        Map<String, Long> modules = new LinkedHashMap<>();
        for (ModuleApplicatif m : ModuleApplicatif.values()) {
            modules.put(m.name(), 0L);
        }
        Map<String, Long> variantes = new LinkedHashMap<>();
        jdbc.query("SELECT module, coalesce(variante, ''), count(*) FROM entreprise_module WHERE actif GROUP BY module, variante",
                rs -> {
                    modules.merge(rs.getString(1), rs.getLong(3), Long::sum);
                    if ("POINT_DE_VENTE".equals(rs.getString(1))) {
                        String v = rs.getString(2).isEmpty() ? "BOUTIQUE" : rs.getString(2);
                        variantes.merge(v, rs.getLong(3), Long::sum);
                    }
                });

        Map<String, Long> pays = liste.stream()
                .collect(Collectors.groupingBy(e -> e.getPays() == null || e.getPays().isBlank() ? "Non renseigne" : e.getPays().trim(),
                        Collectors.counting()));

        LocalDate debutMois = moisCourant.atDay(1);
        List<Activite> activite = List.of(
                activite("VEHICULES", "Vehicules geres", "camion", debutMois),
                activite("MISSIONS", "Missions", "mission", debutMois),
                activite("ARTICLES", "Articles en point de vente", "pdv_article", debutMois),
                activite("VENTES", "Ventes en caisse", "pdv_vente", debutMois),
                activite("COMMANDES", "Commandes en ligne", "mkt_commande", debutMois),
                activite("TRANSACTIONS", "Operations d'agence", "agc_transaction", debutMois),
                activite("POINTAGES", "Pointages de presence", "pres_pointage", debutMois));

        List<Echeance> echeances = abos.stream()
                .filter(a -> a.rappel() || a.paiementEnAttente())
                .filter(a -> parId.containsKey(a.entrepriseId()))
                .sorted(Comparator.comparing(AbonnementVue::fin))
                .map(a -> new Echeance(a.entrepriseId(), a.entrepriseNom(), a.etat(), a.etatLibelle(), a.fin(),
                        a.joursRestants(), a.formuleNom(), a.essai(), a.mensualite(), a.paiementEnAttente()))
                .toList();

        Map<Long, Long> comptes = new java.util.HashMap<>();
        jdbc.query("SELECT entreprise_id, count(*) FROM utilisateur WHERE entreprise_id IS NOT NULL GROUP BY entreprise_id",
                rs -> {
                    comptes.put(rs.getLong(1), rs.getLong(2));
                });
        Function<Entreprise, EntrepriseCourte> courte = e -> new EntrepriseCourte(e.getId(), e.getNom(), e.getPays(),
                e.getCreeLe(), comptes.getOrDefault(e.getId(), 0L),
                aboParId.containsKey(e.getId()) ? aboParId.get(e.getId()).etatLibelle() : null);
        List<EntrepriseCourte> dernieres = liste.stream().filter(e -> e.getCreeLe() != null)
                .sorted(Comparator.comparing(Entreprise::getCreeLe).reversed()).limit(6).map(courte).toList();
        List<EntrepriseCourte> grandes = liste.stream()
                .sorted(Comparator.comparing((Entreprise e) -> comptes.getOrDefault(e.getId(), 0L)).reversed())
                .limit(6).map(courte).toList();

        return new TableauBord(
                devise,
                liste.size(), actives, liste.size() - actives,
                etats.getOrDefault("ESSAI", 0L),
                abos.stream().filter(a -> !a.essai() && !a.lectureSeule()).count(),
                etats.getOrDefault("A_RENOUVELER", 0L), etats.getOrDefault("GRACE", 0L), etats.getOrDefault("EXPIRE", 0L),
                inscriptions.getOrDefault(moisCourant, 0L), inscriptions.getOrDefault(moisPrecedent, 0L),
                valeur("SELECT count(*) FROM utilisateur WHERE entreprise_id IS NOT NULL"),
                valeur("SELECT count(*) FROM utilisateur WHERE entreprise_id IS NOT NULL AND actif"),
                valeur("SELECT count(DISTINCT auteur_id) FROM journal_activite WHERE action = 'CONNEXION' "
                        + "AND date_action >= now() - interval '7 days'"),
                valeur("SELECT count(DISTINCT entreprise_id) FROM journal_activite WHERE date_action >= now() - interval '30 days'"),
                mrr, mrr.multiply(BigDecimal.valueOf(12)),
                revenus.getOrDefault(moisCourant, BigDecimal.ZERO), revenus.getOrDefault(moisPrecedent, BigDecimal.ZERO),
                encaisseAnnee, total[0],
                nombrePayes[0] == 0 ? BigDecimal.ZERO
                        : total[0].divide(BigDecimal.valueOf(nombrePayes[0]), 0, RoundingMode.HALF_UP),
                enAttente, montantEnAttente == null ? BigDecimal.ZERO : montantEnAttente,
                liste.isEmpty() ? BigDecimal.ZERO
                        : BigDecimal.valueOf(payeursDistincts * 100.0 / liste.size()).setScale(1, RoundingMode.HALF_UP),
                serieInscriptions, serieRevenus, parEtat,
                points(formules), points(modules), top(pays, 8), points(variantes),
                parMoyen.entrySet().stream().map(e -> new Point(e.getKey(), e.getKey(), e.getValue())).toList(),
                activite, echeances, dernieres, grandes);
    }

    private Activite activite(String code, String libelle, String table, LocalDate debutMois) {
        return new Activite(code, libelle, valeur("SELECT count(*) FROM " + table),
                valeur("SELECT count(*) FROM " + table + " WHERE cree_le >= '" + debutMois + "'"));
    }

    private long valeur(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    private static List<Point> points(Map<String, Long> m) {
        return m.entrySet().stream().map(e -> new Point(e.getKey(), e.getKey(), BigDecimal.valueOf(e.getValue()))).toList();
    }

    /** Les n plus grands, le reste regroupe sous « Autres ». */
    private static List<Point> top(Map<String, Long> m, int n) {
        List<Map.Entry<String, Long>> tries = m.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).toList();
        List<Point> points = new ArrayList<>(tries.stream().limit(n)
                .map(e -> new Point(e.getKey(), e.getKey(), BigDecimal.valueOf(e.getValue()))).toList());
        long reste = tries.stream().skip(n).mapToLong(Map.Entry::getValue).sum();
        if (reste > 0) {
            points.add(new Point("AUTRES", "Autres", BigDecimal.valueOf(reste)));
        }
        return points;
    }

    private static final String[] MOIS = {"janv.", "fevr.", "mars", "avr.", "mai", "juin", "juil.", "aout",
            "sept.", "oct.", "nov.", "dec."};

    private static String libelleMois(YearMonth m) {
        return MOIS[m.getMonthValue() - 1] + " " + String.valueOf(m.getYear()).substring(2);
    }
}
