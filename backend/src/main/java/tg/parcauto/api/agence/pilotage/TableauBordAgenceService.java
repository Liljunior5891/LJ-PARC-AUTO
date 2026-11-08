package tg.parcauto.api.agence.pilotage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.agence.Agence;
import tg.parcauto.api.agence.PerimetreAgence;
import tg.parcauto.api.agence.catalogue.Reseau;
import tg.parcauto.api.agence.catalogue.ReseauRepository;
import tg.parcauto.api.agence.catalogue.ServiceFinancier;
import tg.parcauto.api.agence.catalogue.ServiceFinancierRepository;
import tg.parcauto.api.agence.cloture.ClotureService;
import tg.parcauto.api.agence.mouvement.MouvementService;
import tg.parcauto.api.agence.operation.TransactionAgence;
import tg.parcauto.api.agence.tresorerie.RegistreService;
import tg.parcauto.api.agence.tresorerie.SoldeService;
import tg.parcauto.api.agence.tresorerie.TypeCompte;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tableau de bord des agences : tresorerie du moment, activite et revenus de
 * la periode, series par jour et repartitions par service, reseau et caissier.
 * <p>
 * Chiffre d'affaires = commissions acquises + frais encaisses + ventes de cartes.
 * Le volume des transactions n'est jamais un revenu. Les depenses, le cout des
 * cartes et les dettes arrivent avec la phase 2 : ils valent zero d'ici la.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TableauBordAgenceService {

    /** Comptes de la tresorerie disponible (hors commissions et fonds d'exploitation). */
    private static final EnumSet<TypeCompte> TRESORERIE = EnumSet.of(TypeCompte.CAISSE_GENERALE, TypeCompte.CAISSE_CAISSIER,
            TypeCompte.FLOAT, TypeCompte.RECHARGE, TypeCompte.BANQUE);

    private final PerimetreAgence perimetre;
    private final SoldeService soldes;
    private final ServiceFinancierRepository services;
    private final ReseauRepository reseaux;
    private final ClotureService clotures;
    private final MouvementService mouvements;
    private final RegistreService registre;
    private final tg.parcauto.api.agence.depense.DepenseAgenceService depensesService;
    private final tg.parcauto.api.agence.credit.CreditService credits;
    private final tg.parcauto.api.agence.carte.CarteService cartes;

    @PersistenceContext
    private EntityManager em;

    public record Filtres(Long agenceId, LocalDate du, LocalDate au, Long serviceId, Long reseauId, Long caissierId) {
    }

    public record Point(LocalDate jour, long nombre, long volume, long commissions) {
    }

    public record PointTresorerie(LocalDate jour, long solde) {
    }

    public record Repartition(Long id, String nom, long nombre, long volume, long commissions) {
    }

    public record TableauBord(LocalDate du, LocalDate au, LocalDate journeeCourante, SoldeService.Totaux tresorerie,
                              long nombre, long volume, long commissions, long ventesCartes, long coutCartes,
                              long chiffreAffaires, long depenses, long fraisFinanciers, long resultatNet, long dettes, long creances,
                              List<Point> parJour,
                              List<PointTresorerie> tresorerieParJour, List<Repartition> parService,
                              List<Repartition> parReseau, List<Repartition> parCaissier, long cloturesAFaire,
                              long mouvementsEnAttente) {
    }

    public TableauBord calculer(Filtres f) {
        LocalDate courante = registre.journee(Instant.now());
        LocalDate au = f.au() != null ? f.au() : courante;
        LocalDate du = f.du() != null ? f.du() : au;
        if (du.isAfter(au)) {
            LocalDate x = du;
            du = au;
            au = x;
        }
        List<Long> agences = f.agenceId() != null ? List.of(perimetre.exiger(f.agenceId()).getId())
                : perimetre.visibles().stream().map(Agence::getId).toList();
        SoldeService.Totaux tresorerie = soldes.etat(f.agenceId()).totaux();
        if (agences.isEmpty()) {
            return new TableauBord(du, au, courante, tresorerie, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of(),
                    List.of(), List.of(), 0, 0);
        }
        Filtres borne = new Filtres(f.agenceId(), du, au, f.serviceId(), f.reseauId(), f.caissierId());

        Object[] totaux = requete("select count(t), coalesce(sum(t.montant), 0), coalesce(sum(t.commission), 0)", "", borne, agences)
                .getSingleResult();
        long nombre = nombre(totaux[0]);
        long volume = nombre(totaux[1]);
        long commissions = nombre(totaux[2]);

        Map<LocalDate, Object[]> jours = requete("select t.journee, count(t), coalesce(sum(t.montant), 0), coalesce(sum(t.commission), 0)",
                " group by t.journee", borne, agences).getResultList().stream()
                .collect(Collectors.toMap(r -> (LocalDate) r[0], r -> r));
        List<Point> parJour = new ArrayList<>();
        LocalDate debutSerie = ChronoUnit.DAYS.between(du, au) > 366 ? au.minusDays(366) : du;
        for (LocalDate j = debutSerie; !j.isAfter(au); j = j.plusDays(1)) {
            Object[] r = jours.get(j);
            parJour.add(r == null ? new Point(j, 0, 0, 0) : new Point(j, nombre(r[1]), nombre(r[2]), nombre(r[3])));
        }

        Map<Long, ServiceFinancier> parId = services.findAll().stream().collect(Collectors.toMap(ServiceFinancier::getId, Function.identity()));
        Map<Long, String> nomsReseaux = reseaux.findAll().stream().collect(Collectors.toMap(Reseau::getId, Reseau::getNom));
        List<Repartition> parService = requete("select t.serviceId, count(t), coalesce(sum(t.montant), 0), coalesce(sum(t.commission), 0)",
                " group by t.serviceId", borne, agences).getResultList().stream()
                .map(r -> new Repartition((Long) r[0], parId.containsKey((Long) r[0]) ? parId.get((Long) r[0]).getNom() : "?",
                        nombre(r[1]), nombre(r[2]), nombre(r[3])))
                .sorted(Comparator.comparingLong(Repartition::commissions).reversed().thenComparing(Repartition::nom))
                .toList();
        List<Repartition> parReseau = requete("select t.reseauId, count(t), coalesce(sum(t.montant), 0), coalesce(sum(t.commission), 0)",
                " and t.reseauId is not null group by t.reseauId", borne, agences).getResultList().stream()
                .map(r -> new Repartition((Long) r[0], nomsReseaux.getOrDefault((Long) r[0], "?"), nombre(r[1]), nombre(r[2]), nombre(r[3])))
                .sorted(Comparator.comparingLong(Repartition::volume).reversed())
                .toList();
        List<Repartition> parCaissier = requete("select t.caissierId, max(t.caissierNom), count(t), coalesce(sum(t.montant), 0), coalesce(sum(t.commission), 0)",
                " group by t.caissierId", borne, agences).getResultList().stream()
                .map(r -> new Repartition((Long) r[0], (String) r[1], nombre(r[2]), nombre(r[3]), nombre(r[4])))
                .sorted(Comparator.comparingLong(Repartition::nombre).reversed())
                .toList();

        java.util.Set<Long> ensemble = new java.util.HashSet<>(agences);
        var totauxCartes = cartes.totaux(ensemble, du, au);
        long ventesCartes = totauxCartes.chiffreAffaires();
        long coutCartes = totauxCartes.cout();
        long chiffreAffaires = commissions + ventesCartes;
        long depenses = depensesService.total(ensemble, du, au);
        long frais = credits.fraisFinanciers(ensemble, du, au);
        var totauxCredits = credits.totaux(ensemble);
        // Resultat net = chiffre d'affaires - cout des cartes vendues - depenses - frais financiers.
        return new TableauBord(du, au, courante, tresorerie, nombre, volume, commissions, ventesCartes, coutCartes, chiffreAffaires,
                depenses, frais, chiffreAffaires - coutCartes - depenses - frais, totauxCredits.dettesRestantes(), totauxCredits.creancesRestantes(), parJour,
                tresorerieParJour(agences, debutSerie, au), parService, parReseau, parCaissier, clotures.aFaire(f.agenceId()).size(),
                mouvements.enAttente());
    }

    /** Tresorerie disponible en fin de chaque journee, reconstituee a partir du registre. */
    private List<PointTresorerie> tresorerieParJour(List<Long> agences, LocalDate du, LocalDate au) {
        List<Object[]> lignes = em.createQuery("""
                        select l.journee, sum(l.montant) from LigneRegistre l, CompteAgence c
                        where c.id = l.compteId and c.agenceId in :agences and c.type in :types and c.bloque = false
                          and l.journee <= :au
                        group by l.journee
                        """, Object[].class)
                .setParameter("agences", agences)
                .setParameter("types", TRESORERIE)
                .setParameter("au", au)
                .getResultList();
        long solde = 0;
        Map<LocalDate, Long> variations = new HashMap<>();
        for (Object[] r : lignes) {
            LocalDate jour = (LocalDate) r[0];
            long montant = nombre(r[1]);
            if (jour.isBefore(du)) {
                solde += montant;
            } else {
                variations.put(jour, montant);
            }
        }
        List<PointTresorerie> serie = new ArrayList<>();
        for (LocalDate j = du; !j.isAfter(au); j = j.plusDays(1)) {
            solde += variations.getOrDefault(j, 0L);
            serie.add(new PointTresorerie(j, solde));
        }
        return serie;
    }

    /** Requete sur les transactions validees de la periode, avec les filtres optionnels. */
    private TypedQuery<Object[]> requete(String select, String suite, Filtres f, List<Long> agences) {
        StringBuilder jpql = new StringBuilder(select).append("""
                 from TransactionAgence t
                where t.agenceId in :agences and t.statut = :statut and t.journee >= :du and t.journee <= :au""");
        Map<String, Object> parametres = new LinkedHashMap<>();
        parametres.put("agences", agences);
        parametres.put("statut", TransactionAgence.Statut.VALIDEE);
        parametres.put("du", f.du());
        parametres.put("au", f.au());
        if (f.serviceId() != null) {
            jpql.append(" and t.serviceId = :service");
            parametres.put("service", f.serviceId());
        }
        if (f.reseauId() != null) {
            jpql.append(" and t.reseauId = :reseau");
            parametres.put("reseau", f.reseauId());
        }
        if (f.caissierId() != null) {
            jpql.append(" and t.caissierId = :caissier");
            parametres.put("caissier", f.caissierId());
        }
        jpql.append(suite);
        TypedQuery<Object[]> q = em.createQuery(jpql.toString(), Object[].class);
        parametres.forEach(q::setParameter);
        return q;
    }

    private static long nombre(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
