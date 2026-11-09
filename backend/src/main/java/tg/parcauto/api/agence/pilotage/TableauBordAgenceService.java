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
    private final tg.parcauto.api.agence.DroitsAgence droits;
    private final SoldeService soldes;
    private final ServiceFinancierRepository services;
    private final ReseauRepository reseaux;
    private final ClotureService clotures;
    private final MouvementService mouvements;
    private final RegistreService registre;
    private final tg.parcauto.api.agence.depense.DepenseAgenceService depensesService;
    private final tg.parcauto.api.agence.credit.CreditService credits;
    private final tg.parcauto.api.agence.carte.CarteService cartes;
    private final tg.parcauto.api.agence.commission.RapprochementService rapprochement;

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
                              long nombre, long volume, long commissions,
                              /** Commissions de la periode deja recues, et commissions en attente de versement (toutes dates). */
                              long commissionsEncaissees, long commissionsEnAttente, long ventesCartes, long coutCartes,
                              long chiffreAffaires, long depenses, long fraisFinanciers, long resultatNet, long dettes, long creances,
                              List<Point> parJour,
                              List<PointTresorerie> tresorerieParJour, List<Repartition> parService,
                              List<Repartition> parReseau, List<Repartition> parCaissier, long cloturesAFaire,
                              long mouvementsEnAttente,
                              /** CAISSIER : ses seules donnees ; AGENCE : une agence ; TOUTES : toutes les agences visibles. */
                              String perimetre) {
    }

    public TableauBord calculer(Filtres demande) {
        // Sans le droit de voir toutes les operations, le caissier ne voit que les siennes :
        // ses transactions, sa caisse, ses depenses et ses ventes de cartes.
        boolean caissier = !droits.courants().contains(tg.parcauto.api.agence.PermissionAgence.HISTORIQUE_COMPLET);
        Long moi = perimetre.utilisateurCourant();
        Filtres f = caissier ? new Filtres(demande.agenceId(), demande.du(), demande.au(), demande.serviceId(), demande.reseauId(), moi)
                : demande;
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
        String portee = caissier ? "CAISSIER" : (f.agenceId() != null || agences.size() == 1 ? "AGENCE" : "TOUTES");
        List<Long> mesCaisses = caissier && !agences.isEmpty() ? em.createQuery("""
                        select c.id from CompteAgence c
                        where c.agenceId in :agences and c.type = :type and c.utilisateurId = :moi""", Long.class)
                .setParameter("agences", agences).setParameter("type", TypeCompte.CAISSE_CAISSIER).setParameter("moi", moi)
                .getResultList() : List.of();
        SoldeService.Totaux tresorerie;
        if (caissier) {
            long enCaisse = mesCaisses.isEmpty() ? 0 : em.createQuery(
                            "select coalesce(sum(c.solde), 0) from CompteAgence c where c.id in :ids", Long.class)
                    .setParameter("ids", mesCaisses).getSingleResult();
            tresorerie = new SoldeService.Totaux(enCaisse, 0, 0, enCaisse, 0, 0);
        } else {
            tresorerie = soldes.etat(f.agenceId()).totaux();
        }
        if (agences.isEmpty()) {
            return new TableauBord(du, au, courante, tresorerie, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), List.of(), List.of(),
                    List.of(), List.of(), 0, 0, portee);
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
        if (caissier) {
            // Ses ventes de cartes et ses depenses ; ni dettes, ni rapprochement, ni mouvements a valider.
            Object[] v = em.createQuery("""
                            select coalesce(sum(v.montant), 0), coalesce(sum(v.coutTotal), 0) from VenteCarte v
                            where v.agenceId in :agences and v.caissierId = :moi
                              and v.statut = tg.parcauto.api.agence.carte.VenteCarte.Statut.VALIDEE
                              and v.journee >= :du and v.journee <= :au""", Object[].class)
                    .setParameter("agences", agences).setParameter("moi", moi).setParameter("du", du).setParameter("au", au)
                    .getSingleResult();
            long ventesCartes = nombre(v[0]);
            long coutCartes = nombre(v[1]);
            long depenses = nombre(em.createQuery("""
                            select coalesce(sum(d.montant), 0) from DepenseAgence d
                            where d.agenceId in :agences and d.auteurId = :moi
                              and d.statut = tg.parcauto.api.agence.depense.DepenseAgence.Statut.VALIDEE
                              and d.journee >= :du and d.journee <= :au""", Long.class)
                    .setParameter("agences", agences).setParameter("moi", moi).setParameter("du", du).setParameter("au", au)
                    .getSingleResult());
            long chiffreAffaires = commissions + ventesCartes;
            return new TableauBord(du, au, courante, tresorerie, nombre, volume, commissions, 0, 0, ventesCartes, coutCartes,
                    chiffreAffaires, depenses, 0, chiffreAffaires - coutCartes - depenses, 0, 0, parJour,
                    tresorerieParJour(mesCaisses, debutSerie, au), parService, parReseau, parCaissier,
                    clotures.aFaire(f.agenceId()).size(), 0, portee);
        }
        var totauxCartes = cartes.totaux(ensemble, du, au);
        long ventesCartes = totauxCartes.chiffreAffaires();
        long coutCartes = totauxCartes.cout();
        long chiffreAffaires = commissions + ventesCartes;
        long depenses = depensesService.total(ensemble, du, au);
        long frais = credits.fraisFinanciers(ensemble, du, au);
        var totauxCredits = credits.totaux(ensemble);
        List<Long> comptesTresorerie = em.createQuery(
                        "select c.id from CompteAgence c where c.agenceId in :agences and c.type in :types and c.bloque = false", Long.class)
                .setParameter("agences", agences).setParameter("types", TRESORERIE).getResultList();
        // Resultat net = chiffre d'affaires - cout des cartes vendues - depenses - frais financiers.
        return new TableauBord(du, au, courante, tresorerie, nombre, volume, commissions, rapprochement.encaissees(ensemble, du, au),
                rapprochement.totalEnAttente(ensemble), ventesCartes, coutCartes, chiffreAffaires,
                depenses, frais, chiffreAffaires - coutCartes - depenses - frais, totauxCredits.dettesRestantes(), totauxCredits.creancesRestantes(), parJour,
                tresorerieParJour(comptesTresorerie, debutSerie, au), parService, parReseau, parCaissier, clotures.aFaire(f.agenceId()).size(),
                mouvements.enAttente(), portee);
    }

    /** Solde des comptes donnes en fin de chaque journee, reconstitue a partir du registre. */
    private List<PointTresorerie> tresorerieParJour(List<Long> comptes, LocalDate du, LocalDate au) {
        List<Object[]> lignes = comptes.isEmpty() ? List.of() : em.createQuery("""
                        select l.journee, sum(l.montant) from LigneRegistre l
                        where l.compteId in :comptes and l.journee <= :au
                        group by l.journee
                        """, Object[].class)
                .setParameter("comptes", comptes)
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
