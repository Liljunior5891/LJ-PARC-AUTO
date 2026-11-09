package tg.parcauto.api.agence.pilotage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.agence.Agence;
import tg.parcauto.api.agence.DroitsAgence;
import tg.parcauto.api.agence.PerimetreAgence;
import tg.parcauto.api.agence.PermissionAgence;
import tg.parcauto.api.agence.caisse.SessionCaisseAgence;
import tg.parcauto.api.agence.carte.CarteService;
import tg.parcauto.api.agence.carte.ProduitCarte;
import tg.parcauto.api.agence.carte.VenteCarte;
import tg.parcauto.api.agence.catalogue.Reseau;
import tg.parcauto.api.agence.catalogue.ServiceFinancier;
import tg.parcauto.api.agence.catalogue.TypeOperation;
import tg.parcauto.api.agence.cloture.ClotureAgence;
import tg.parcauto.api.agence.credit.CreditService;
import tg.parcauto.api.agence.depense.CategorieDepenseAgence;
import tg.parcauto.api.agence.depense.DepenseAgence;
import tg.parcauto.api.agence.mouvement.MouvementAgence;
import tg.parcauto.api.agence.operation.TransactionAgence;
import tg.parcauto.api.agence.tresorerie.CompteAgence;
import tg.parcauto.api.agence.tresorerie.JourneeCaisse;
import tg.parcauto.api.pointdevente.rapport.Rapport;
import tg.parcauto.api.pointdevente.rapport.Rapport.Format;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Rapports des agences, affiches a l'ecran et exportes a l'identique en Excel,
 * PDF ou CSV. Chaque rapport porte l'agence, la periode et la date de
 * generation. Les rapports financiers complets (synthese, commissions,
 * depenses, credits, rentabilite) demandent le droit correspondant ; les
 * autres se contentent de l'acces aux soldes (rapport partiel du superviseur).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RapportAgenceService {

    private static final DateTimeFormatter JJ_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter GENERE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    /** Plafond de lignes des listes detaillees (un export reste lisible). */
    private static final int MAX_LIGNES = 20_000;

    private final PerimetreAgence perimetre;
    private final DroitsAgence droits;
    private final CarteService cartes;
    private final CreditService credits;

    @PersistenceContext
    private EntityManager em;

    public enum Type {
        SYNTHESE("Synthese de la periode", "Activite, revenus, depenses et resultat jour par jour (ou mois par mois)", true),
        TRANSACTIONS("Transactions", "Detail des transactions validees et annulees", false),
        COMMISSIONS("Commissions", "Commissions par service, reseau et operation", true),
        DEPENSES("Depenses", "Depenses de la periode par categorie", true),
        MOUVEMENTS("Mouvements", "Mouvements entre comptes et regularisations", false),
        CREDITS("Credits et dettes", "Credits, apports, avances et dettes, restant du", true),
        STOCKS("Stock de cartes", "Lots de cartes, quantites restantes et valeur au cout reel", false),
        VENTES_CARTES("Ventes de cartes", "Ventes de cartes : chiffre d'affaires, cout reel, marge", false),
        RECHARGES("Recharges de cartes", "Recharges de cartes et commissions", false),
        RENTABILITE("Rentabilite", "Revenus par service, marge des cartes, charges et resultat", true),
        ECARTS("Ecarts de caisse", "Ecarts des fermetures de caisse et des clotures", false);

        private final String titre;
        private final String description;
        private final boolean complet;

        Type(String titre, String description, boolean complet) {
            this.titre = titre;
            this.description = description;
            this.complet = complet;
        }

        public String getTitre() {
            return titre;
        }

        public String getDescription() {
            return description;
        }

        public boolean isComplet() {
            return complet;
        }
    }

    public record Catalogue(String code, String titre, String description, boolean autorise) {
    }

    public List<Catalogue> catalogue() {
        return java.util.Arrays.stream(Type.values()).map(t -> new Catalogue(t.name(), t.titre, t.description, autorise(t))).toList();
    }

    public Rapport rapport(Type type, Long agenceId, LocalDate du, LocalDate au) {
        if (!autorise(type)) {
            throw new AccessDeniedException("Votre profil n'a pas acces au rapport " + type.titre);
        }
        List<Agence> agences = agenceId != null ? List.of(perimetre.exiger(agenceId)) : perimetre.visibles();
        Map<Long, String> noms = agences.stream().collect(Collectors.toMap(Agence::getId, Agence::getNom));
        Set<Long> ids = noms.isEmpty() ? Set.of(-1L) : noms.keySet();
        String portee = agenceId != null ? agences.get(0).getNom() : (agences.size() == 1 ? agences.get(0).getNom() : "Toutes les agences");
        String sousTitre = portee + " — du " + du.format(JJ_MM_AAAA) + " au " + au.format(JJ_MM_AAAA) + " — genere le "
                + LocalDateTime.now(JourneeCaisse.FUSEAU).format(GENERE);
        Rapport.Constructeur r = new Rapport.Constructeur(type.name(), type.titre, sousTitre);
        switch (type) {
            case SYNTHESE -> synthese(r, ids, du, au);
            case TRANSACTIONS -> transactions(r, ids, noms, du, au);
            case COMMISSIONS -> commissions(r, ids, du, au);
            case DEPENSES -> depenses(r, ids, noms, du, au);
            case MOUVEMENTS -> mouvements(r, ids, noms, du, au);
            case CREDITS -> credits(r, ids, noms);
            case STOCKS -> stocks(r, ids, noms);
            case VENTES_CARTES -> ventesCartes(r, ids, noms, du, au);
            case RECHARGES -> recharges(r, ids, noms, du, au);
            case RENTABILITE -> rentabilite(r, ids, du, au);
            case ECARTS -> ecarts(r, ids, noms, du, au);
        }
        return r.construire();
    }

    private boolean autorise(Type type) {
        Set<PermissionAgence> d = droits.courants();
        return type.complet ? d.contains(PermissionAgence.RAPPORTS_COMPLETS) : d.contains(PermissionAgence.SOLDES_CONSULTER);
    }

    // ------------------------------------------------------------- rapports

    /** Jour par jour jusqu'a 62 jours, mois par mois au-dela (rapports hebdomadaire, mensuel, annuel). */
    private void synthese(Rapport.Constructeur r, Set<Long> agences, LocalDate du, LocalDate au) {
        boolean parMois = ChronoUnit.DAYS.between(du, au) > 62;
        Function<LocalDate, String> cle = j -> parMois ? YearMonth.from(j).toString() : j.toString();
        Map<String, long[]> lignes = new TreeMap<>();
        // [transactions, volume, commissions, ventes de cartes, cout des cartes, depenses, frais financiers]
        for (LocalDate j = du; !j.isAfter(au); j = j.plusDays(1)) {
            lignes.putIfAbsent(cle.apply(j), new long[7]);
        }
        for (Object[] t : liste("select t.journee, count(t), sum(t.montant), sum(t.commission) from TransactionAgence t where t.agenceId in :agences"
                + " and t.statut = :validee and t.journee between :du and :au group by t.journee", agences, du, au, Map.of("validee", TransactionAgence.Statut.VALIDEE))) {
            long[] l = lignes.get(cle.apply((LocalDate) t[0]));
            l[0] += n(t[1]);
            l[1] += n(t[2]);
            l[2] += n(t[3]);
        }
        for (Object[] v : liste("select v.journee, sum(v.montant), sum(v.coutTotal) from VenteCarte v where v.agenceId in :agences and v.statut = :validee"
                + " and v.journee between :du and :au group by v.journee", agences, du, au, Map.of("validee", VenteCarte.Statut.VALIDEE))) {
            long[] l = lignes.get(cle.apply((LocalDate) v[0]));
            l[3] += n(v[1]);
            l[4] += n(v[2]);
        }
        for (Object[] d : liste("select d.journee, sum(d.montant) from DepenseAgence d where d.agenceId in :agences and d.statut = :validee"
                + " and d.journee between :du and :au group by d.journee", agences, du, au, Map.of("validee", DepenseAgence.Statut.VALIDEE))) {
            lignes.get(cle.apply((LocalDate) d[0]))[5] += n(d[1]);
        }
        for (Object[] f : liste("select r.journee, sum(r.interets) from RemboursementAgence r, CreditAgence c where c.id = r.creditId"
                + " and c.type <> :avance and r.agenceId in :agences and r.statut = :valide and r.journee between :du and :au group by r.journee",
                agences, du, au, Map.of("avance", tg.parcauto.api.agence.credit.CreditAgence.Type.AVANCE,
                        "valide", tg.parcauto.api.agence.credit.RemboursementAgence.Statut.VALIDE))) {
            lignes.get(cle.apply((LocalDate) f[0]))[6] += n(f[1]);
        }
        r.colonne("periode", parMois ? "Mois" : "Jour", parMois ? Format.TEXTE : Format.DATE)
                .colonne("nombre", "Transactions", Format.NOMBRE).colonne("volume", "Volume", Format.MONTANT)
                .colonne("commissions", "Commissions", Format.MONTANT).colonne("cartes", "Ventes de cartes", Format.MONTANT)
                .colonne("ca", "Chiffre d'affaires", Format.MONTANT).colonne("cout", "Cout des cartes", Format.MONTANT)
                .colonne("depenses", "Depenses", Format.MONTANT).colonne("frais", "Frais financiers", Format.MONTANT)
                .colonne("resultat", "Resultat net", Format.MONTANT);
        long[] total = new long[7];
        lignes.forEach((k, l) -> {
            for (int i = 0; i < 7; i++) {
                total[i] += l[i];
            }
            r.ligne(parMois ? libelleMois(YearMonth.parse(k)) : LocalDate.parse(k), l[0], l[1], l[2], l[3], l[2] + l[3], l[4], l[5], l[6],
                    l[2] + l[3] - l[4] - l[5] - l[6]);
        });
        long ca = total[2] + total[3];
        long resultat = ca - total[4] - total[5] - total[6];
        r.total("nombre", total[0]).total("volume", total[1]).total("commissions", total[2]).total("cartes", total[3]).total("ca", ca)
                .total("cout", total[4]).total("depenses", total[5]).total("frais", total[6]).total("resultat", resultat);
        r.indicateur("Chiffre d'affaires", ca, Format.MONTANT).indicateur("Charges", total[4] + total[5] + total[6], Format.MONTANT)
                .indicateur("Resultat net", resultat, Format.MONTANT).indicateur("Volume (non un revenu)", total[1], Format.MONTANT);
    }

    private void transactions(Rapport.Constructeur r, Set<Long> agences, Map<Long, String> noms, LocalDate du, LocalDate au) {
        NomsCatalogue c = catalogueNoms();
        r.colonne("date", "Date", Format.DATE_HEURE).colonne("reference", "Reference", Format.TEXTE).colonne("agence", "Agence", Format.TEXTE)
                .colonne("service", "Service", Format.TEXTE).colonne("operation", "Operation", Format.TEXTE)
                .colonne("montant", "Montant", Format.MONTANT).colonne("commission", "Commission", Format.MONTANT)
                .colonne("operateur", "Ref. operateur", Format.TEXTE).colonne("client", "Client", Format.TEXTE)
                .colonne("caissier", "Caissier", Format.TEXTE).colonne("statut", "Statut", Format.TEXTE);
        long montant = 0;
        long commissions = 0;
        long nombre = 0;
        for (TransactionAgence t : em.createQuery("select t from TransactionAgence t where t.agenceId in :agences and t.journee between :du and :au"
                        + " order by t.dateOperation", TransactionAgence.class)
                .setParameter("agences", agences).setParameter("du", du).setParameter("au", au).setMaxResults(MAX_LIGNES).getResultList()) {
            boolean valide = t.getStatut() == TransactionAgence.Statut.VALIDEE;
            r.ligne(t.getDateOperation(), t.getReference(), noms.get(t.getAgenceId()),
                    t.getReseauId() != null ? c.reseaux.get(t.getReseauId()) : c.services.get(t.getServiceId()),
                    c.types.get(t.getTypeOperationId()), t.getMontant(), t.getCommission(), t.getReferenceOperateur(),
                    joindre(t.getClientNom(), t.getClientTelephone()), t.getCaissierNom(), valide ? "Validee" : "Annulee");
            if (valide) {
                nombre++;
                montant += t.getMontant();
                commissions += t.getCommission();
            }
        }
        r.total("montant", montant).total("commission", commissions);
        r.indicateur("Transactions validees", nombre, Format.NOMBRE).indicateur("Volume", montant, Format.MONTANT)
                .indicateur("Commissions", commissions, Format.MONTANT);
    }

    private void commissions(Rapport.Constructeur r, Set<Long> agences, LocalDate du, LocalDate au) {
        NomsCatalogue c = catalogueNoms();
        r.colonne("service", "Service", Format.TEXTE).colonne("reseau", "Reseau", Format.TEXTE).colonne("operation", "Operation", Format.TEXTE)
                .colonne("nombre", "Nombre", Format.NOMBRE).colonne("volume", "Volume", Format.MONTANT)
                .colonne("commissions", "Commissions acquises", Format.MONTANT).colonne("attente", "Dont en attente", Format.MONTANT)
                .colonne("modifiees", "Commissions modifiees", Format.NOMBRE);
        long total = 0;
        long volume = 0;
        long attente = 0;
        List<Object[]> lignes = liste("select t.serviceId, t.reseauId, t.typeOperationId, count(t), sum(t.montant), sum(t.commission),"
                + " sum(case when t.commission <> t.commissionProposee then 1 else 0 end),"
                + " sum(case when t.commissionStatut = tg.parcauto.api.agence.operation.TransactionAgence.StatutCommission.ACQUISE then t.commission else 0 end)"
                + " from TransactionAgence t where t.agenceId in :agences"
                + " and t.statut = :validee and t.journee between :du and :au group by t.serviceId, t.reseauId, t.typeOperationId",
                agences, du, au, Map.of("validee", TransactionAgence.Statut.VALIDEE));
        lignes.sort(Comparator.comparingLong((Object[] x) -> n(x[5])).reversed());
        for (Object[] l : lignes) {
            r.ligne(c.services.get((Long) l[0]), l[1] == null ? "" : c.reseaux.get((Long) l[1]), c.types.get((Long) l[2]), n(l[3]), n(l[4]), n(l[5]),
                    n(l[7]), n(l[6]));
            attente += n(l[7]);
            total += n(l[5]);
            volume += n(l[4]);
        }
        r.total("volume", volume).total("commissions", total).total("attente", attente);
        r.indicateur("Commissions acquises", total, Format.MONTANT).indicateur("Encaissees", total - attente, Format.MONTANT)
                .indicateur("En attente de versement", attente, Format.MONTANT).indicateur("Taux moyen", volume == 0 ? 0
                : java.math.BigDecimal.valueOf(total * 100.0 / volume).setScale(2, java.math.RoundingMode.HALF_UP), Format.POURCENT);
    }

    private void depenses(Rapport.Constructeur r, Set<Long> agences, Map<Long, String> noms, LocalDate du, LocalDate au) {
        Map<Long, String> categories = em.createQuery("select c from CategorieDepenseAgence c", CategorieDepenseAgence.class).getResultList().stream()
                .collect(Collectors.toMap(CategorieDepenseAgence::getId, CategorieDepenseAgence::getNom));
        r.colonne("date", "Date", Format.DATE_HEURE).colonne("numero", "Numero", Format.TEXTE).colonne("agence", "Agence", Format.TEXTE)
                .colonne("categorie", "Categorie", Format.TEXTE).colonne("libelle", "Libelle", Format.TEXTE)
                .colonne("beneficiaire", "Beneficiaire", Format.TEXTE).colonne("montant", "Montant", Format.MONTANT);
        long total = 0;
        Map<String, Long> parCategorie = new TreeMap<>();
        for (DepenseAgence d : em.createQuery("select d from DepenseAgence d where d.agenceId in :agences and d.statut = :validee"
                        + " and d.journee between :du and :au order by d.dateDepense", DepenseAgence.class)
                .setParameter("agences", agences).setParameter("validee", DepenseAgence.Statut.VALIDEE).setParameter("du", du).setParameter("au", au)
                .setMaxResults(MAX_LIGNES).getResultList()) {
            String cat = categories.getOrDefault(d.getCategorieId(), "?");
            r.ligne(d.getDateDepense(), d.getNumero(), noms.get(d.getAgenceId()), cat, d.getLibelle(), d.getBeneficiaire(), d.getMontant());
            total += d.getMontant();
            parCategorie.merge(cat, d.getMontant(), Long::sum);
        }
        r.total("montant", total);
        r.indicateur("Total des charges", total, Format.MONTANT);
        parCategorie.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(3)
                .forEach(e -> r.indicateur(e.getKey(), e.getValue(), Format.MONTANT));
    }

    private void mouvements(Rapport.Constructeur r, Set<Long> agences, Map<Long, String> noms, LocalDate du, LocalDate au) {
        Map<Long, String> comptes = nomsComptes(agences);
        r.colonne("date", "Date", Format.DATE_HEURE).colonne("reference", "Reference", Format.TEXTE).colonne("agence", "Agence", Format.TEXTE)
                .colonne("type", "Type", Format.TEXTE).colonne("de", "De", Format.TEXTE).colonne("vers", "Vers", Format.TEXTE)
                .colonne("montant", "Montant", Format.MONTANT).colonne("motif", "Motif", Format.TEXTE).colonne("statut", "Statut", Format.TEXTE)
                .colonne("valide", "Valide par", Format.TEXTE);
        for (MouvementAgence m : em.createQuery("select m from MouvementAgence m where m.agenceId in :agences and m.dateMouvement >= :debut"
                        + " and m.dateMouvement < :fin order by m.dateMouvement", MouvementAgence.class)
                .setParameter("agences", agences).setParameter("debut", du.atStartOfDay(JourneeCaisse.FUSEAU).toInstant())
                .setParameter("fin", au.plusDays(1).atStartOfDay(JourneeCaisse.FUSEAU).toInstant()).setMaxResults(MAX_LIGNES).getResultList()) {
            r.ligne(m.getDateMouvement(), m.getReference(), noms.get(m.getAgenceId()), m.getType().getLibelle(),
                    m.getCompteSourceId() == null ? "" : comptes.get(m.getCompteSourceId()),
                    m.getCompteDestinationId() == null ? "" : comptes.get(m.getCompteDestinationId()), m.getMontant(), m.getMotif(),
                    m.getStatut().name().replace('_', ' ').toLowerCase(), m.getValideParNom());
        }
    }

    private void credits(Rapport.Constructeur r, Set<Long> agences, Map<Long, String> noms) {
        r.colonne("numero", "Numero", Format.TEXTE).colonne("agence", "Agence", Format.TEXTE).colonne("type", "Type", Format.TEXTE)
                .colonne("tiers", "Tiers", Format.TEXTE).colonne("debut", "Date", Format.DATE).colonne("initial", "Montant initial", Format.MONTANT)
                .colonne("rembourse", "Capital rembourse", Format.MONTANT).colonne("interets", "Interets payes", Format.MONTANT)
                .colonne("restant", "Restant du", Format.MONTANT).colonne("echeance", "Prochaine echeance", Format.DATE)
                .colonne("statut", "Statut", Format.TEXTE);
        long dettes = 0;
        long creances = 0;
        for (Long agence : agences) {
            if (agence < 0) {
                continue;
            }
            for (CreditService.CreditDto c : credits.lister(agence, null)) {
                r.ligne(c.numero(), noms.get(c.agenceId()), c.typeLibelle(), c.tiersNom(), c.dateDebut(), c.montantInitial(), c.capitalRembourse(),
                        c.interetsPayes(), c.capitalRestant(), c.prochaineEcheance(), c.statut().name().replace('_', ' ').toLowerCase());
                if (c.statut() == tg.parcauto.api.agence.credit.CreditAgence.Statut.EN_COURS) {
                    if (c.dette() && c.remboursable()) {
                        dettes += c.capitalRestant();
                    } else if (!c.dette()) {
                        creances += c.capitalRestant();
                    }
                }
            }
        }
        r.indicateur("Dettes restantes", dettes, Format.MONTANT).indicateur("Avances a recuperer", creances, Format.MONTANT);
    }

    private void stocks(Rapport.Constructeur r, Set<Long> agences, Map<Long, String> noms) {
        Map<Long, String> produits = em.createQuery("select p from ProduitCarte p", ProduitCarte.class).getResultList().stream()
                .collect(Collectors.toMap(ProduitCarte::getId, ProduitCarte::getNom));
        r.colonne("agence", "Agence", Format.TEXTE).colonne("carte", "Carte", Format.TEXTE).colonne("lot", "Lot", Format.TEXTE)
                .colonne("entree", "Entre le", Format.DATE).colonne("initial", "Entre", Format.NOMBRE).colonne("reste", "Reste", Format.NOMBRE)
                .colonne("cout", "Cout unitaire", Format.MONTANT).colonne("valeur", "Valeur restante", Format.MONTANT);
        long cartesRestantes = 0;
        long valeur = 0;
        for (var l : em.createQuery("select l from LotCarte l where l.agenceId in :agences order by l.agenceId, l.produitId, l.dateEntree",
                tg.parcauto.api.agence.carte.LotCarte.class).setParameter("agences", agences).getResultList()) {
            long v = (long) l.getQuantiteRestante() * l.getCoutUnitaire();
            r.ligne(noms.get(l.getAgenceId()), produits.get(l.getProduitId()), l.getNumero(), l.getDateEntree(), l.getQuantiteInitiale(),
                    l.getQuantiteRestante(), l.getCoutUnitaire(), v);
            cartesRestantes += l.getQuantiteRestante();
            valeur += v;
        }
        r.total("reste", cartesRestantes).total("valeur", valeur);
        r.indicateur("Cartes en stock", cartesRestantes, Format.NOMBRE).indicateur("Valeur au cout reel", valeur, Format.MONTANT);
    }

    private void ventesCartes(Rapport.Constructeur r, Set<Long> agences, Map<Long, String> noms, LocalDate du, LocalDate au) {
        Map<Long, String> produits = em.createQuery("select p from ProduitCarte p", ProduitCarte.class).getResultList().stream()
                .collect(Collectors.toMap(ProduitCarte::getId, ProduitCarte::getNom));
        r.colonne("date", "Date", Format.DATE_HEURE).colonne("reference", "Reference", Format.TEXTE).colonne("agence", "Agence", Format.TEXTE)
                .colonne("carte", "Carte", Format.TEXTE).colonne("quantite", "Quantite", Format.NOMBRE).colonne("montant", "Montant", Format.MONTANT)
                .colonne("cout", "Cout reel", Format.MONTANT).colonne("marge", "Marge", Format.MONTANT).colonne("caissier", "Caissier", Format.TEXTE);
        long q = 0, montant = 0, cout = 0, marge = 0;
        for (VenteCarte v : em.createQuery("select v from VenteCarte v where v.agenceId in :agences and v.statut = :validee"
                        + " and v.journee between :du and :au order by v.dateVente", VenteCarte.class)
                .setParameter("agences", agences).setParameter("validee", VenteCarte.Statut.VALIDEE).setParameter("du", du).setParameter("au", au)
                .setMaxResults(MAX_LIGNES).getResultList()) {
            r.ligne(v.getDateVente(), v.getReference(), noms.get(v.getAgenceId()), produits.get(v.getProduitId()), v.getQuantite(), v.getMontant(),
                    v.getCoutTotal(), v.getMarge(), v.getCaissierNom());
            q += v.getQuantite();
            montant += v.getMontant();
            cout += v.getCoutTotal();
            marge += v.getMarge();
        }
        r.total("quantite", q).total("montant", montant).total("cout", cout).total("marge", marge);
        r.indicateur("Cartes vendues", q, Format.NOMBRE).indicateur("Chiffre d'affaires", montant, Format.MONTANT)
                .indicateur("Marge brute", marge, Format.MONTANT);
    }

    private void recharges(Rapport.Constructeur r, Set<Long> agences, Map<Long, String> noms, LocalDate du, LocalDate au) {
        r.colonne("date", "Date", Format.DATE_HEURE).colonne("reference", "Reference", Format.TEXTE).colonne("agence", "Agence", Format.TEXTE)
                .colonne("carte", "Carte", Format.TEXTE).colonne("montant", "Montant", Format.MONTANT).colonne("commission", "Commission", Format.MONTANT)
                .colonne("operateur", "Ref. operateur", Format.TEXTE).colonne("caissier", "Caissier", Format.TEXTE);
        long montant = 0, commissions = 0, nombre = 0;
        for (TransactionAgence t : em.createQuery("select t from TransactionAgence t where t.agenceId in :agences and t.compteVirtuel = :recharge"
                        + " and t.statut = :validee and t.journee between :du and :au order by t.dateOperation", TransactionAgence.class)
                .setParameter("agences", agences).setParameter("recharge", TypeOperation.CompteVirtuel.RECHARGE)
                .setParameter("validee", TransactionAgence.Statut.VALIDEE).setParameter("du", du).setParameter("au", au)
                .setMaxResults(MAX_LIGNES).getResultList()) {
            r.ligne(t.getDateOperation(), t.getReference(), noms.get(t.getAgenceId()), t.getCarteMasquee(), t.getMontant(), t.getCommission(),
                    t.getReferenceOperateur(), t.getCaissierNom());
            nombre++;
            montant += t.getMontant();
            commissions += t.getCommission();
        }
        r.total("montant", montant).total("commission", commissions);
        CarteService.Suivi suivi = cartes.suivi(agences.size() == 1 && !agences.contains(-1L) ? agences.iterator().next() : null, du, au);
        r.indicateur("Recharges", nombre, Format.NOMBRE).indicateur("Montant recharge", montant, Format.MONTANT)
                .indicateur("Commissions (seul revenu)", commissions, Format.MONTANT)
                .indicateur("Solde de recharge disponible", suivi.recharge().soldeActuel(), Format.MONTANT);
    }

    private void rentabilite(Rapport.Constructeur r, Set<Long> agences, LocalDate du, LocalDate au) {
        NomsCatalogue c = catalogueNoms();
        r.colonne("poste", "Poste", Format.TEXTE).colonne("nombre", "Nombre", Format.NOMBRE).colonne("volume", "Volume", Format.MONTANT)
                .colonne("revenu", "Revenu", Format.MONTANT).colonne("charge", "Charge", Format.MONTANT).colonne("resultat", "Resultat", Format.MONTANT);
        long revenus = 0;
        long charges = 0;
        List<Object[]> parService = liste("select t.serviceId, count(t), sum(t.montant), sum(t.commission) from TransactionAgence t where t.agenceId in :agences"
                + " and t.statut = :validee and t.journee between :du and :au group by t.serviceId", agences, du, au,
                Map.of("validee", TransactionAgence.Statut.VALIDEE));
        parService.sort(Comparator.comparingLong((Object[] x) -> n(x[3])).reversed());
        for (Object[] s : parService) {
            r.ligne("Commissions " + c.services.get((Long) s[0]), n(s[1]), n(s[2]), n(s[3]), 0L, n(s[3]));
            revenus += n(s[3]);
        }
        CarteService.TotauxVentes ventes = cartes.totaux(agences, du, au);
        if (ventes.ventes() > 0) {
            r.ligne("Ventes de cartes (marge brute)", ventes.cartes(), ventes.chiffreAffaires(), ventes.chiffreAffaires(), ventes.cout(), ventes.marge());
            revenus += ventes.chiffreAffaires();
            charges += ventes.cout();
        }
        long depenses = n(em.createQuery("select coalesce(sum(d.montant), 0) from DepenseAgence d where d.agenceId in :agences and d.statut = :validee"
                        + " and d.journee between :du and :au")
                .setParameter("agences", agences).setParameter("validee", DepenseAgence.Statut.VALIDEE).setParameter("du", du).setParameter("au", au)
                .getSingleResult());
        long frais = credits.fraisFinanciers(agences, du, au);
        r.ligne("Depenses", null, null, 0L, depenses, -depenses);
        r.ligne("Frais financiers (interets)", null, null, 0L, frais, -frais);
        charges += depenses + frais;
        r.total("revenu", revenus).total("charge", charges).total("resultat", revenus - charges);
        r.indicateur("Chiffre d'affaires", revenus, Format.MONTANT).indicateur("Charges", charges, Format.MONTANT)
                .indicateur("Resultat net", revenus - charges, Format.MONTANT);
    }

    private void ecarts(Rapport.Constructeur r, Set<Long> agences, Map<Long, String> noms, LocalDate du, LocalDate au) {
        r.colonne("date", "Date", Format.DATE).colonne("agence", "Agence", Format.TEXTE).colonne("origine", "Origine", Format.TEXTE)
                .colonne("compte", "Caisse ou compte", Format.TEXTE).colonne("attendu", "Attendu", Format.MONTANT).colonne("reel", "Reel", Format.MONTANT)
                .colonne("ecart", "Ecart", Format.MONTANT).colonne("justification", "Justification", Format.TEXTE);
        long total = 0;
        for (SessionCaisseAgence s : em.createQuery("select s from SessionCaisseAgence s where s.agenceId in :agences and s.statut = :fermee"
                        + " and s.fermeeLe >= :debut and s.fermeeLe < :fin and s.ecart <> 0 order by s.fermeeLe", SessionCaisseAgence.class)
                .setParameter("agences", agences).setParameter("fermee", SessionCaisseAgence.Statut.FERMEE)
                .setParameter("debut", du.atStartOfDay(JourneeCaisse.FUSEAU).toInstant())
                .setParameter("fin", au.plusDays(1).atStartOfDay(JourneeCaisse.FUSEAU).toInstant()).getResultList()) {
            r.ligne(s.getFermeeLe().atZone(JourneeCaisse.FUSEAU).toLocalDate(), noms.get(s.getAgenceId()), "Fermeture " + s.getNumero(),
                    "Caisse de " + s.getCaissierNom(), s.getMontantTheorique(), s.getMontantCompte(), s.getEcart(), s.getJustification());
            total += s.getEcart();
        }
        for (ClotureAgence c : em.createQuery("select c from ClotureAgence c where c.agenceId in :agences and c.journee between :du and :au"
                + " order by c.journee", ClotureAgence.class).setParameter("agences", agences).setParameter("du", du).setParameter("au", au).getResultList()) {
            for (var l : c.getLignes()) {
                if (l.getEcart() != null && l.getEcart() != 0) {
                    r.ligne(c.getJournee(), noms.get(c.getAgenceId()), "Cloture (" + c.getStatut().name().toLowerCase() + ")", l.getLibelle(),
                            l.getSoldeTheorique(), l.getSoldeReel(), l.getEcart(), l.getJustification());
                    total += l.getEcart();
                }
            }
        }
        r.total("ecart", total);
        r.indicateur("Ecart cumule", total, Format.MONTANT);
    }

    // ---------------------------------------------------------------- outils

    private List<Object[]> liste(String jpql, Set<Long> agences, LocalDate du, LocalDate au, Map<String, Object> autres) {
        var q = em.createQuery(jpql, Object[].class).setParameter("agences", agences).setParameter("du", du).setParameter("au", au);
        autres.forEach(q::setParameter);
        return new ArrayList<>(q.getResultList());
    }

    private record NomsCatalogue(Map<Long, String> services, Map<Long, String> reseaux, Map<Long, String> types) {
    }

    private NomsCatalogue catalogueNoms() {
        return new NomsCatalogue(
                em.createQuery("select s from ServiceFinancier s", ServiceFinancier.class).getResultList().stream()
                        .collect(Collectors.toMap(ServiceFinancier::getId, ServiceFinancier::getNom)),
                em.createQuery("select r from Reseau r", Reseau.class).getResultList().stream().collect(Collectors.toMap(Reseau::getId, Reseau::getNom)),
                em.createQuery("select t from TypeOperation t", TypeOperation.class).getResultList().stream()
                        .collect(Collectors.toMap(TypeOperation::getId, TypeOperation::getLibelle)));
    }

    private Map<Long, String> nomsComptes(Set<Long> agences) {
        return em.createQuery("select c from CompteAgence c where c.agenceId in :agences", CompteAgence.class).setParameter("agences", agences)
                .getResultList().stream().collect(Collectors.toMap(CompteAgence::getId, CompteAgence::getLibelle));
    }

    private static String joindre(String a, String b) {
        String s = ((a == null ? "" : a) + " " + (b == null ? "" : b)).trim();
        return s.isEmpty() ? null : s;
    }

    private static String libelleMois(YearMonth m) {
        String nom = m.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRANCE);
        return Character.toUpperCase(nom.charAt(0)) + nom.substring(1) + " " + m.getYear();
    }

    private static long n(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }
}
