package tg.parcauto.api.pointdevente.rapport;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleService;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;
import tg.parcauto.api.pointdevente.approvisionnement.CommandeFournisseur;
import tg.parcauto.api.pointdevente.approvisionnement.CommandeFournisseurRepository;
import tg.parcauto.api.pointdevente.caisse.CaisseService;
import tg.parcauto.api.pointdevente.caisse.SessionCaisse;
import tg.parcauto.api.pointdevente.caisse.SessionCaisseRepository;
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;
import tg.parcauto.api.pointdevente.restauration.Commande;
import tg.parcauto.api.pointdevente.restauration.CommandeRepository;
import tg.parcauto.api.pointdevente.restauration.EcranPreparation;
import tg.parcauto.api.pointdevente.restauration.EcranPreparationRepository;
import tg.parcauto.api.pointdevente.restauration.LigneCommande;
import tg.parcauto.api.pointdevente.restauration.LigneCommandeRepository;
import tg.parcauto.api.pointdevente.restauration.RestaurationDto;
import tg.parcauto.api.pointdevente.stock.MouvementStock;
import tg.parcauto.api.pointdevente.stock.MouvementStockRepository;
import tg.parcauto.api.pointdevente.vente.Vente;
import tg.parcauto.api.pointdevente.vente.VenteRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static tg.parcauto.api.pointdevente.rapport.Rapport.Format.DATE;
import static tg.parcauto.api.pointdevente.rapport.Rapport.Format.DATE_HEURE;
import static tg.parcauto.api.pointdevente.rapport.Rapport.Format.MONTANT;
import static tg.parcauto.api.pointdevente.rapport.Rapport.Format.NOMBRE;
import static tg.parcauto.api.pointdevente.rapport.Rapport.Format.POURCENT;
import static tg.parcauto.api.pointdevente.rapport.Rapport.Format.TEXTE;

/**
 * Rapports du point de vente : ventes (commandes, produits, categories,
 * vendeurs, jours), stock (etat, mouvements), achats, caisses et commandes de
 * table. Chaque rapport est calcule sur la periode demandee, decoupee dans le
 * fuseau du poste qui consulte.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RapportPdvService {

    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Au-dela, l'ecran et les exports se limitent aux lignes les plus recentes. */
    private static final int LIGNES_MAX = 20_000;

    private final VenteRepository venteRepository;
    private final ArticleRepository articleRepository;
    private final MouvementStockRepository mouvementRepository;
    private final CommandeFournisseurRepository commandeFournisseurRepository;
    private final SessionCaisseRepository sessionRepository;
    private final CaisseService caisseService;
    private final CommandeRepository commandeRepository;
    private final ModuleService moduleService;
    private final EcranPreparationRepository ecranRepository;
    private final LigneCommandeRepository ligneCommandeRepository;
    private final tg.parcauto.api.pointdevente.catalogue.CategorieRepository categorieRepository;
    private final tg.parcauto.api.pointdevente.depense.DepensePdvRepository depenseRepository;
    private final tg.parcauto.api.pointdevente.depense.DepensePdvService depenseService;
    private final tg.parcauto.api.pointdevente.fournisseur.ReglementFournisseurRepository reglementFournisseurRepository;
    private final tg.parcauto.api.pointdevente.fournisseur.FournisseurService fournisseurService;

    public record Catalogue(String type, String libelle, String famille, boolean periode, boolean parArticle,
                            boolean parEcran) {
    }

    /** Rapports proposes a l'etablissement ; les commandes de table seulement en restauration. */
    public List<Catalogue> catalogue() {
        boolean restauration = restauration();
        return java.util.Arrays.stream(TypeRapport.values())
                .filter(t -> !t.isRestauration() || restauration)
                .map(t -> new Catalogue(t.name(), t.getLibelle(), t.getFamille(), t.isPeriode(), t.isParArticle(),
                        t.isParEcran()))
                .toList();
    }

    public Rapport calculer(TypeRapport type, Instant debut, Instant fin, ZoneId fuseau) {
        return calculer(type, debut, fin, fuseau, null);
    }

    /** {@code article} : le produit de la fiche de stock, ignore par les autres rapports. */
    public Rapport calculer(TypeRapport type, Instant debut, Instant fin, ZoneId fuseau, Long article) {
        return calculer(type, debut, fin, fuseau, article, null);
    }

    /** {@code ecran} : l'ecran de preparation des rapports de production, ignore par les autres. */
    public Rapport calculer(TypeRapport type, Instant debut, Instant fin, ZoneId fuseau, Long article, Long ecran) {
        String periode = type.isPeriode()
                ? "Du " + JOUR.format(debut.atZone(fuseau)) + " au " + JOUR.format(fin.minusSeconds(1).atZone(fuseau))
                : "Au " + JOUR.format(Instant.now().atZone(fuseau));
        return switch (type) {
            case VENTES -> ventes(debut, fin, periode);
            case VENTES_PRODUITS -> ventesProduits(debut, fin, periode);
            case VENTES_CATEGORIES -> ventesCategories(debut, fin, periode);
            case VENTES_VENDEURS -> ventesVendeurs(debut, fin, periode);
            case VENTES_JOURS -> ventesJours(debut, fin, fuseau, periode);
            case PRODUITS -> produits(periode);
            case CATEGORIES -> categories(periode);
            case STOCK -> stock(periode);
            case FICHE_STOCK -> ficheStock(article, debut, fin, periode);
            case MOUVEMENTS -> mouvements(debut, fin, periode);
            case COMMANDES_FOURNISSEURS -> commandesFournisseurs(debut, fin, fuseau, periode);
            case CAISSES -> caisses(debut, fin, periode);
            case DETTES_FOURNISSEURS -> dettesFournisseurs(periode);
            case REGLEMENTS_FOURNISSEURS -> reglementsFournisseurs(jour(debut, fuseau), jourFin(fin, fuseau), periode);
            case DEPENSES -> depenses(jour(debut, fuseau), jourFin(fin, fuseau), periode);
            case DEPENSES_CATEGORIES -> depensesCategories(jour(debut, fuseau), jourFin(fin, fuseau), periode);
            case RESULTAT -> resultat(debut, fin, fuseau, periode);
            case COMMANDES_TABLE -> {
                exigerRestauration();
                yield commandesTable(debut, fin, periode);
            }
            case PREPARATION_BONS -> {
                exigerRestauration();
                yield bonsEcran(ecran(ecran), debut, fin, periode);
            }
            case PREPARATION_PRODUITS -> {
                exigerRestauration();
                yield produitsEcran(ecran(ecran), debut, fin, periode);
            }
        };
    }

    // ------------------------------------------------------------ ventes

    private Rapport ventes(Instant debut, Instant fin, String periode) {
        Rapport.Constructeur r = new Rapport.Constructeur("VENTES", "Commandes encaissees", periode)
                .colonne("numero", "Commande", TEXTE)
                .colonne("date", "Date", DATE_HEURE)
                .colonne("vendeur", "Vendeur", TEXTE)
                .colonne("table", "Table", TEXTE)
                .colonne("client", "Client", TEXTE)
                .colonne("mode", "Paiement", TEXTE)
                .colonne("statut", "Statut", TEXTE)
                .colonne("brut", "Montant brut", MONTANT)
                .colonne("remise", "Remise", MONTANT)
                .colonne("total", "Total", MONTANT);
        BigDecimal ca = BigDecimal.ZERO;
        BigDecimal remises = BigDecimal.ZERO;
        long validees = 0;
        long annulees = 0;
        for (Vente v : venteRepository.periode(debut, fin, PageRequest.of(0, LIGNES_MAX)).getContent()) {
            boolean annulee = v.getStatut() == Vente.Statut.ANNULEE;
            r.ligne(v.getNumero(), v.getDateVente(), v.getCaissierNom(), v.getTableNom(), v.getClientNom(),
                    v.getModePaiement().getLibelle(), annulee ? "Annulee" : "Encaissee",
                    v.getTotalBrut(), v.getRemise(), v.getTotal());
            if (annulee) {
                annulees++;
            } else {
                validees++;
                ca = ca.add(v.getTotal());
                remises = remises.add(v.getRemise());
            }
        }
        return r.total("total", ca).total("remise", remises)
                .indicateur("Chiffre d'affaires", ca, MONTANT)
                .indicateur("Commandes encaissees", validees, NOMBRE)
                .indicateur("Panier moyen", validees == 0 ? BigDecimal.ZERO : ca.divide(BigDecimal.valueOf(validees), 0, RoundingMode.HALF_UP), MONTANT)
                .indicateur("Commandes annulees", annulees, NOMBRE)
                .construire();
    }

    private Rapport ventesProduits(Instant debut, Instant fin, String periode) {
        Rapport.Constructeur r = new Rapport.Constructeur("VENTES_PRODUITS", "Ventes par produit", periode)
                .colonne("code", "Code", TEXTE)
                .colonne("produit", "Produit", TEXTE)
                .colonne("categorie", "Categorie", TEXTE)
                .colonne("quantite", "Quantite vendue", NOMBRE)
                .colonne("ca", "Chiffre d'affaires", MONTANT)
                .colonne("cout", "Cout d'achat", MONTANT)
                .colonne("marge", "Marge", MONTANT)
                .colonne("taux", "Taux de marge", POURCENT);
        List<Object[]> lignes = venteRepository.ventesParProduit(debut, fin);
        BigDecimal[] t = cumulVentes(r, lignes, true);
        return totauxMarge(r, t).indicateur("Produits vendus", lignes.size(), NOMBRE).construire();
    }

    private Rapport ventesCategories(Instant debut, Instant fin, String periode) {
        Rapport.Constructeur r = new Rapport.Constructeur("VENTES_CATEGORIES", "Ventes par categorie", periode)
                .colonne("categorie", "Categorie", TEXTE)
                .colonne("quantite", "Quantite vendue", NOMBRE)
                .colonne("ca", "Chiffre d'affaires", MONTANT)
                .colonne("cout", "Cout d'achat", MONTANT)
                .colonne("marge", "Marge", MONTANT)
                .colonne("taux", "Taux de marge", POURCENT);
        BigDecimal[] t = cumulVentes(r, venteRepository.ventesCategoriesAvecCout(debut, fin), false);
        return totauxMarge(r, t).construire();
    }

    /** Lignes [libelles..., quantite, CA, cout] completees de la marge et du taux ; renvoie les cumuls. */
    private static BigDecimal[] cumulVentes(Rapport.Constructeur r, List<Object[]> lignes, boolean avecCode) {
        BigDecimal quantite = BigDecimal.ZERO;
        BigDecimal ca = BigDecimal.ZERO;
        BigDecimal cout = BigDecimal.ZERO;
        int texte = avecCode ? 3 : 1;
        for (Object[] l : lignes) {
            BigDecimal q = (BigDecimal) l[texte];
            BigDecimal c = (BigDecimal) l[texte + 1];
            BigDecimal k = (BigDecimal) l[texte + 2];
            BigDecimal m = c.subtract(k);
            Object[] valeurs = new Object[texte + 5];
            System.arraycopy(l, 0, valeurs, 0, texte);
            // La derniere colonne de texte est la categorie.
            if (valeurs[texte - 1] == null) {
                valeurs[texte - 1] = "Sans categorie";
            }
            valeurs[texte] = q;
            valeurs[texte + 1] = c;
            valeurs[texte + 2] = k;
            valeurs[texte + 3] = m;
            valeurs[texte + 4] = taux(m, c);
            r.ligne(valeurs);
            quantite = quantite.add(q);
            ca = ca.add(c);
            cout = cout.add(k);
        }
        return new BigDecimal[]{quantite, ca, cout};
    }

    private static Rapport.Constructeur totauxMarge(Rapport.Constructeur r, BigDecimal[] t) {
        BigDecimal marge = t[1].subtract(t[2]);
        return r.total("quantite", t[0]).total("ca", t[1]).total("cout", t[2]).total("marge", marge)
                .total("taux", taux(marge, t[1]))
                .indicateur("Chiffre d'affaires", t[1], MONTANT)
                .indicateur("Cout d'achat", t[2], MONTANT)
                .indicateur("Marge brute", marge, MONTANT)
                .indicateur("Taux de marge", taux(marge, t[1]), POURCENT);
    }

    private Rapport ventesVendeurs(Instant debut, Instant fin, String periode) {
        Rapport.Constructeur r = new Rapport.Constructeur("VENTES_VENDEURS", "Ventes par vendeur", periode)
                .colonne("vendeur", "Vendeur", TEXTE)
                .colonne("commandes", "Commandes encaissees", NOMBRE)
                .colonne("ca", "Chiffre d'affaires", MONTANT)
                .colonne("panier", "Panier moyen", MONTANT)
                .colonne("annulees", "Commandes annulees", NOMBRE);
        long commandes = 0;
        BigDecimal ca = BigDecimal.ZERO;
        long annulees = 0;
        for (Object[] l : venteRepository.ventesParVendeur(debut, fin)) {
            long n = ((Number) l[1]).longValue();
            BigDecimal c = (BigDecimal) l[2];
            long a = ((Number) l[3]).longValue();
            r.ligne(l[0], n, c, n == 0 ? BigDecimal.ZERO : c.divide(BigDecimal.valueOf(n), 0, RoundingMode.HALF_UP), a);
            commandes += n;
            ca = ca.add(c);
            annulees += a;
        }
        return r.total("commandes", commandes).total("ca", ca).total("annulees", annulees)
                .indicateur("Chiffre d'affaires", ca, MONTANT)
                .indicateur("Commandes encaissees", commandes, NOMBRE)
                .indicateur("Commandes annulees", annulees, NOMBRE)
                .construire();
    }

    private Rapport ventesJours(Instant debut, Instant fin, ZoneId fuseau, String periode) {
        Rapport.Constructeur r = new Rapport.Constructeur("VENTES_JOURS", "Ventes par jour", periode)
                .colonne("jour", "Jour", DATE)
                .colonne("commandes", "Commandes", NOMBRE)
                .colonne("ca", "Chiffre d'affaires", MONTANT)
                .colonne("cout", "Cout d'achat", MONTANT)
                .colonne("marge", "Marge", MONTANT)
                .colonne("panier", "Panier moyen", MONTANT);
        Map<LocalDate, BigDecimal[]> jours = new TreeMap<>(Comparator.reverseOrder());
        for (Object[] t : venteRepository.ticketsAvecCout(debut, fin)) {
            LocalDate jour = ((Instant) t[0]).atZone(fuseau).toLocalDate();
            BigDecimal[] c = jours.computeIfAbsent(jour, j -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            c[0] = c[0].add(BigDecimal.ONE);
            c[1] = c[1].add((BigDecimal) t[1]);
            c[2] = c[2].add((BigDecimal) t[2]);
        }
        BigDecimal n = BigDecimal.ZERO;
        BigDecimal ca = BigDecimal.ZERO;
        BigDecimal cout = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, BigDecimal[]> e : jours.entrySet()) {
            BigDecimal[] c = e.getValue();
            r.ligne(e.getKey(), c[0], c[1], c[2], c[1].subtract(c[2]), c[1].divide(c[0], 0, RoundingMode.HALF_UP));
            n = n.add(c[0]);
            ca = ca.add(c[1]);
            cout = cout.add(c[2]);
        }
        return r.total("commandes", n).total("ca", ca).total("cout", cout).total("marge", ca.subtract(cout))
                .indicateur("Chiffre d'affaires", ca, MONTANT)
                .indicateur("Jours avec ventes", jours.size(), NOMBRE)
                .indicateur("Moyenne par jour", jours.isEmpty() ? BigDecimal.ZERO : ca.divide(BigDecimal.valueOf(jours.size()), 0, RoundingMode.HALF_UP), MONTANT)
                .indicateur("Marge brute", ca.subtract(cout), MONTANT)
                .construire();
    }

    // ------------------------------------------------------------- stock

    private Rapport stock(String periode) {
        Rapport.Constructeur r = new Rapport.Constructeur("STOCK", "Etat du stock", periode)
                .colonne("code", "Code", TEXTE)
                .colonne("produit", "Produit", TEXTE)
                .colonne("categorie", "Categorie", TEXTE)
                .colonne("unite", "Unite", TEXTE)
                .colonne("stock", "Stock", NOMBRE)
                .colonne("seuil", "Seuil d'alerte", NOMBRE)
                .colonne("prixAchat", "Cout d'achat", MONTANT)
                .colonne("prixVente", "Prix de vente", MONTANT)
                .colonne("valeurAchat", "Valeur au cout", MONTANT)
                .colonne("valeurVente", "Valeur a la vente", MONTANT)
                .colonne("etat", "Etat", TEXTE);
        BigDecimal valeurAchat = BigDecimal.ZERO;
        BigDecimal valeurVente = BigDecimal.ZERO;
        long ruptures = 0;
        long bas = 0;
        List<Article> articles = articleRepository.findAll().stream()
                .filter(a -> a.isActif() && a.isSuiviStock())
                .sorted(Comparator.comparing(a -> a.getNom().toLowerCase()))
                .toList();
        for (Article a : articles) {
            BigDecimal achat = a.getPrixAchat() == null ? BigDecimal.ZERO : a.getPrixAchat().multiply(a.getStockActuel());
            BigDecimal vente = a.getPrixVente().multiply(a.getStockActuel());
            String etat = a.getStockActuel().signum() <= 0 ? "Rupture" : a.stockBas() ? "Sous le seuil" : "Normal";
            if (a.getStockActuel().signum() <= 0) {
                ruptures++;
            } else if (a.stockBas()) {
                bas++;
            }
            r.ligne(a.getCode(), a.getNom(), a.getCategorie() == null ? "" : a.getCategorie().getNom(), a.getUnite(),
                    a.getStockActuel(), a.getSeuilAlerte(), a.getPrixAchat(), a.getPrixVente(), achat, vente, etat);
            valeurAchat = valeurAchat.add(achat);
            valeurVente = valeurVente.add(vente);
        }
        return r.total("valeurAchat", valeurAchat).total("valeurVente", valeurVente)
                .indicateur("Produits suivis", articles.size(), NOMBRE)
                .indicateur("Valeur du stock (cout)", valeurAchat, MONTANT)
                .indicateur("En rupture", ruptures, NOMBRE)
                .indicateur("Sous le seuil", bas, NOMBRE)
                .construire();
    }

    private Rapport produits(String periode) {
        Rapport.Constructeur r = new Rapport.Constructeur("PRODUITS", "Liste des produits", periode)
                .colonne("code", "Code", TEXTE)
                .colonne("produit", "Produit", TEXTE)
                .colonne("categorie", "Categorie", TEXTE)
                .colonne("codeBarres", "Code-barres", TEXTE)
                .colonne("unite", "Unite", TEXTE)
                .colonne("prixAchat", "Cout d'achat", MONTANT)
                .colonne("prixVente", "Prix de vente", MONTANT)
                .colonne("marge", "Marge", MONTANT)
                .colonne("taux", "Taux de marge", POURCENT)
                .colonne("stock", "Stock", NOMBRE)
                .colonne("seuil", "Seuil d'alerte", NOMBRE)
                .colonne("statut", "Statut", TEXTE);
        List<Article> articles = articleRepository.findAll().stream()
                .sorted(Comparator.comparing((Article a) -> !a.isActif()).thenComparing(a -> a.getNom().toLowerCase()))
                .toList();
        long actifs = 0;
        long suivis = 0;
        for (Article a : articles) {
            BigDecimal marge = a.getPrixAchat() == null ? null : a.getPrixVente().subtract(a.getPrixAchat());
            BigDecimal taux = marge == null || a.getPrixVente().signum() == 0 ? null
                    : marge.multiply(BigDecimal.valueOf(100)).divide(a.getPrixVente(), 1, RoundingMode.HALF_UP);
            r.ligne(a.getCode(), a.getNom(), a.getCategorie() == null ? "" : a.getCategorie().getNom(), a.getCodeBarres(),
                    a.getUnite(), a.getPrixAchat(), a.getPrixVente(), marge, taux,
                    a.isSuiviStock() ? a.getStockActuel() : null, a.isSuiviStock() ? a.getSeuilAlerte() : null,
                    a.isActif() ? "Actif" : "Inactif");
            actifs += a.isActif() ? 1 : 0;
            suivis += a.isSuiviStock() ? 1 : 0;
        }
        return r.indicateur("Produits", articles.size(), NOMBRE)
                .indicateur("Actifs", actifs, NOMBRE)
                .indicateur("Suivis en stock", suivis, NOMBRE)
                .construire();
    }

    /** Categories du catalogue, dans l'ordre de la caisse, avec le nombre de produits de chacune. */
    private Rapport categories(String periode) {
        boolean restauration = restauration();
        Rapport.Constructeur r = new Rapport.Constructeur("CATEGORIES", "Liste des categories", periode)
                .colonne("ordre", "Ordre", NOMBRE)
                .colonne("categorie", "Categorie", TEXTE)
                .colonne("icone", "Icone", TEXTE)
                .colonne("couleur", "Couleur", TEXTE);
        if (restauration) {
            r.colonne("poste", "Poste de preparation", TEXTE);
        }
        r.colonne("produits", "Produits", NOMBRE)
                .colonne("statut", "Statut", TEXTE);
        java.util.Map<Long, Long> parCategorie = articleRepository.findAll().stream()
                .filter(a -> a.getCategorie() != null)
                .collect(java.util.stream.Collectors.groupingBy(a -> a.getCategorie().getId(),
                        java.util.stream.Collectors.counting()));
        List<tg.parcauto.api.pointdevente.catalogue.Categorie> liste = categorieRepository.findAllByOrderByOrdreAscNomAsc();
        long actives = 0;
        for (tg.parcauto.api.pointdevente.catalogue.Categorie c : liste) {
            long produits = parCategorie.getOrDefault(c.getId(), 0L);
            if (restauration) {
                r.ligne(c.getOrdre(), c.getNom(), c.getIcone(), c.getCouleur(), c.getPoste().getLibelle(), produits,
                        c.isActif() ? "Active" : "Inactive");
            } else {
                r.ligne(c.getOrdre(), c.getNom(), c.getIcone(), c.getCouleur(), produits, c.isActif() ? "Active" : "Inactive");
            }
            actives += c.isActif() ? 1 : 0;
        }
        return r.indicateur("Categories", liste.size(), NOMBRE)
                .indicateur("Actives", actives, NOMBRE)
                .construire();
    }

    /**
     * Fiche de stock d'un produit : le stock au debut de la periode, chaque
     * entree et sortie avec le solde qui en resulte, puis le stock a la fin.
     */
    private Rapport ficheStock(Long articleId, Instant debut, Instant fin, String periode) {
        if (articleId == null) {
            throw new BusinessException("Choisissez le produit de la fiche de stock");
        }
        Article a = articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException("Produit introuvable"));
        List<MouvementStock> mouvements = mouvementRepository
                .findByArticleIdAndDateMouvementGreaterThanEqualAndDateMouvementLessThanOrderByDateMouvementAscIdAsc(
                        articleId, debut, fin);
        // Stock au debut : celui laisse par le dernier mouvement anterieur, a defaut celui d'avant le premier
        // mouvement de la periode, a defaut le stock actuel (aucun mouvement du tout).
        BigDecimal initial = mouvementRepository
                .findFirstByArticleIdAndDateMouvementLessThanOrderByDateMouvementDescIdDesc(articleId, debut)
                .map(MouvementStock::getStockApres)
                .orElseGet(() -> mouvements.isEmpty() ? a.getStockActuel()
                        : mouvements.get(0).getStockApres().subtract(mouvements.get(0).getQuantite()));
        String sousTitre = a.getCode() + (a.getUnite() == null ? "" : " · " + a.getUnite()) + " · " + periode;
        Rapport.Constructeur r = new Rapport.Constructeur("FICHE_STOCK", "Fiche de stock : " + a.getNom(), sousTitre)
                .colonne("date", "Date", DATE_HEURE)
                .colonne("operation", "Operation", TEXTE)
                .colonne("motif", "Motif / reference", TEXTE)
                .colonne("entree", "Entree", NOMBRE)
                .colonne("sortie", "Sortie", NOMBRE)
                .colonne("solde", "Stock", NOMBRE)
                .colonne("cout", "Cout unitaire", MONTANT)
                .colonne("auteur", "Par", TEXTE);
        r.ligne(debut, "Stock au debut", null, null, null, initial, null, null);
        BigDecimal entrees = BigDecimal.ZERO;
        BigDecimal sorties = BigDecimal.ZERO;
        BigDecimal solde = initial;
        for (MouvementStock m : mouvements) {
            boolean entree = m.getQuantite().signum() > 0;
            solde = m.getStockApres();
            r.ligne(m.getDateMouvement(), m.getType().getLibelle(), m.getMotif(),
                    entree ? m.getQuantite() : null, entree ? null : m.getQuantite().negate(),
                    solde, m.getCoutUnitaire(), m.getCreePar());
            if (entree) {
                entrees = entrees.add(m.getQuantite());
            } else {
                sorties = sorties.add(m.getQuantite().negate());
            }
        }
        BigDecimal valeur = a.getPrixAchat() == null ? BigDecimal.ZERO : a.getPrixAchat().multiply(solde);
        return r.total("entree", entrees).total("sortie", sorties).total("solde", solde)
                .indicateur("Stock au debut", initial, NOMBRE)
                .indicateur("Entrees", entrees, NOMBRE)
                .indicateur("Sorties", sorties, NOMBRE)
                .indicateur("Stock a la fin", solde, NOMBRE)
                .indicateur("Valeur au cout", valeur, MONTANT)
                .construire();
    }

    private Rapport mouvements(Instant debut, Instant fin, String periode) {
        Rapport.Constructeur r = new Rapport.Constructeur("MOUVEMENTS", "Mouvements de stock", periode)
                .colonne("date", "Date", DATE_HEURE)
                .colonne("code", "Code", TEXTE)
                .colonne("produit", "Produit", TEXTE)
                .colonne("type", "Mouvement", TEXTE)
                .colonne("quantite", "Quantite", NOMBRE)
                .colonne("stockApres", "Stock apres", NOMBRE)
                .colonne("cout", "Cout unitaire", MONTANT)
                .colonne("motif", "Motif", TEXTE)
                .colonne("auteur", "Par", TEXTE);
        BigDecimal entrees = BigDecimal.ZERO;
        BigDecimal sorties = BigDecimal.ZERO;
        List<MouvementStock> mouvements = mouvementRepository
                .findByDateMouvementGreaterThanEqualAndDateMouvementLessThanOrderByDateMouvementDescIdDesc(debut, fin);
        for (MouvementStock m : mouvements.stream().limit(LIGNES_MAX).toList()) {
            r.ligne(m.getDateMouvement(), m.getArticle().getCode(), m.getArticle().getNom(), m.getType().getLibelle(),
                    m.getQuantite(), m.getStockApres(), m.getCoutUnitaire(), m.getMotif(), m.getCreePar());
            if (m.getQuantite().signum() > 0) {
                entrees = entrees.add(m.getQuantite());
            } else {
                sorties = sorties.add(m.getQuantite().negate());
            }
        }
        return r.indicateur("Mouvements", mouvements.size(), NOMBRE)
                .indicateur("Quantites entrees", entrees, NOMBRE)
                .indicateur("Quantites sorties", sorties, NOMBRE)
                .construire();
    }

    // ------------------------------------------------------------ achats

    private Rapport commandesFournisseurs(Instant debut, Instant fin, ZoneId fuseau, String periode) {
        Rapport.Constructeur r = new Rapport.Constructeur("COMMANDES_FOURNISSEURS", "Commandes fournisseurs", periode)
                .colonne("numero", "Commande", TEXTE)
                .colonne("date", "Date", DATE)
                .colonne("fournisseur", "Fournisseur", TEXTE)
                .colonne("produits", "Produits", NOMBRE)
                .colonne("montant", "Montant commande", MONTANT)
                .colonne("statut", "Statut", TEXTE)
                .colonne("livraison", "Livraison prevue", DATE)
                .colonne("recue", "Receptionnee le", DATE_HEURE)
                .colonne("valeurRecue", "Valeur recue", MONTANT);
        BigDecimal commande = BigDecimal.ZERO;
        BigDecimal recu = BigDecimal.ZERO;
        long enCours = 0;
        List<CommandeFournisseur> commandes = commandeFournisseurRepository.periode(
                debut.atZone(fuseau).toLocalDate(), fin.minusSeconds(1).atZone(fuseau).toLocalDate());
        for (CommandeFournisseur c : commandes) {
            BigDecimal valeurRecue = c.getLignes().stream()
                    .filter(l -> l.getQuantiteRecue() != null && l.getCoutUnitaire() != null)
                    .map(l -> l.getQuantiteRecue().multiply(l.getCoutUnitaire()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String statut = switch (c.getStatut()) {
                case EN_COURS -> "A receptionner";
                case RECUE -> "Receptionnee";
                case ANNULEE -> "Annulee";
            };
            r.ligne(c.getNumero(), c.getDateCommande(), c.getFournisseur(), c.getLignes().size(), c.total(), statut,
                    c.getLivraisonPrevue(), c.getRecueLe(), c.getStatut() == CommandeFournisseur.Statut.RECUE ? valeurRecue : null);
            if (c.getStatut() != CommandeFournisseur.Statut.ANNULEE) {
                commande = commande.add(c.total());
            }
            recu = recu.add(valeurRecue);
            if (c.getStatut() == CommandeFournisseur.Statut.EN_COURS) {
                enCours++;
            }
        }
        return r.total("montant", commande).total("valeurRecue", recu)
                .indicateur("Commandes", commandes.size(), NOMBRE)
                .indicateur("Montant commande", commande, MONTANT)
                .indicateur("Valeur receptionnee", recu, MONTANT)
                .indicateur("A receptionner", enCours, NOMBRE)
                .construire();
    }

    // ------------------------------------------------- dettes fournisseurs

    /** Ce qui reste du a chaque fournisseur, commande par commande, les echeances depassees d'abord. */
    private Rapport dettesFournisseurs(String periode) {
        Rapport.Constructeur r = new Rapport.Constructeur("DETTES_FOURNISSEURS", "Dettes fournisseurs", periode)
                .colonne("fournisseur", "Fournisseur", TEXTE)
                .colonne("commande", "Commande", TEXTE)
                .colonne("recue", "Receptionnee le", DATE_HEURE)
                .colonne("montant", "Valeur recue", MONTANT)
                .colonne("regle", "Deja regle", MONTANT)
                .colonne("reste", "Reste du", MONTANT)
                .colonne("echeance", "Echeance", DATE)
                .colonne("situation", "Situation", TEXTE);
        java.util.Map<Long, String> noms = new java.util.HashMap<>();
        for (CommandeFournisseur c : commandeFournisseurRepository.dettes(null)) {
            noms.put(c.getId(), c.getFiche() == null ? c.getFournisseur() : c.getFiche().getNom());
        }
        BigDecimal du = BigDecimal.ZERO;
        BigDecimal retard = BigDecimal.ZERO;
        List<tg.parcauto.api.pointdevente.fournisseur.FournisseurService.Dette> dettes =
                new java.util.ArrayList<>(fournisseurService.toutesLesDettes());
        dettes.sort(java.util.Comparator
                .comparing((tg.parcauto.api.pointdevente.fournisseur.FournisseurService.Dette d) -> !d.enRetard())
                .thenComparing(d -> noms.getOrDefault(d.commandeId(), "").toLowerCase()));
        for (var d : dettes) {
            r.ligne(noms.getOrDefault(d.commandeId(), ""), d.numero(), d.recueLe(), d.montantRecu(), d.regle(),
                    d.reste(), d.echeance(), d.enRetard() ? "En retard" : "A echoir");
            du = du.add(d.reste());
            if (d.enRetard()) {
                retard = retard.add(d.reste());
            }
        }
        return r.total("reste", du)
                .indicateur("Reste du", du, MONTANT)
                .indicateur("Dont en retard", retard, MONTANT)
                .indicateur("Commandes a payer", dettes.size(), NOMBRE)
                .construire();
    }

    private Rapport reglementsFournisseurs(java.time.LocalDate debut, java.time.LocalDate fin, String periode) {
        Rapport.Constructeur r = new Rapport.Constructeur("REGLEMENTS_FOURNISSEURS", "Reglements fournisseurs", periode)
                .colonne("numero", "Reglement", TEXTE)
                .colonne("date", "Date", DATE)
                .colonne("fournisseur", "Fournisseur", TEXTE)
                .colonne("commande", "Commande", TEXTE)
                .colonne("mode", "Paiement", TEXTE)
                .colonne("reference", "Reference", TEXTE)
                .colonne("montant", "Montant", MONTANT)
                .colonne("auteur", "Par", TEXTE)
                .colonne("statut", "Statut", TEXTE);
        BigDecimal total = BigDecimal.ZERO;
        long nombre = 0;
        for (var g : reglementFournisseurRepository.periode(debut, fin)) {
            boolean valide = g.getStatut() == tg.parcauto.api.pointdevente.fournisseur.ReglementFournisseur.Statut.VALIDE;
            r.ligne(g.getNumero(), g.getDateReglement(), g.getFournisseur().getNom(), g.getCommande().getNumero(),
                    g.getMode().getLibelle(), g.getReference(), g.getMontant(), g.getAuteurNom(),
                    valide ? "Valide" : "Annule");
            if (valide) {
                total = total.add(g.getMontant());
                nombre++;
            }
        }
        return r.total("montant", total)
                .indicateur("Total regle", total, MONTANT)
                .indicateur("Paiements", nombre, NOMBRE)
                .construire();
    }

    // ----------------------------------------------------------- depenses

    private Rapport depenses(java.time.LocalDate debut, java.time.LocalDate fin, String periode) {
        Rapport.Constructeur r = new Rapport.Constructeur("DEPENSES", "Depenses", periode)
                .colonne("numero", "Depense", TEXTE)
                .colonne("date", "Date", DATE)
                .colonne("categorie", "Categorie", TEXTE)
                .colonne("libelle", "Libelle", TEXTE)
                .colonne("beneficiaire", "Beneficiaire", TEXTE)
                .colonne("mode", "Paiement", TEXTE)
                .colonne("caisse", "Caisse", TEXTE)
                .colonne("montant", "Montant", MONTANT)
                .colonne("auteur", "Saisie par", TEXTE)
                .colonne("statut", "Statut", TEXTE);
        BigDecimal total = BigDecimal.ZERO;
        long nombre = 0;
        for (var d : depenseRepository.periode(debut, fin)) {
            boolean valide = d.getStatut() == tg.parcauto.api.pointdevente.depense.DepensePdv.Statut.VALIDE;
            String beneficiaire = d.getBeneficiaire() != null ? d.getBeneficiaire()
                    : d.getFournisseur() != null ? d.getFournisseur().getNom() : null;
            r.ligne(d.getNumero(), d.getDateDepense(), d.getCategorie().getNom(), d.getLibelle(), beneficiaire,
                    d.getMode().getLibelle(), d.getSession() == null ? null : d.getSession().getCaisse().getNom(),
                    d.getMontant(), d.getAuteurNom(), valide ? "Valide" : "Annulee");
            if (valide) {
                total = total.add(d.getMontant());
                nombre++;
            }
        }
        return r.total("montant", total)
                .indicateur("Total des depenses", total, MONTANT)
                .indicateur("Depenses", nombre, NOMBRE)
                .construire();
    }

    private Rapport depensesCategories(java.time.LocalDate debut, java.time.LocalDate fin, String periode) {
        Rapport.Constructeur r = new Rapport.Constructeur("DEPENSES_CATEGORIES", "Depenses par categorie", periode)
                .colonne("categorie", "Categorie", TEXTE)
                .colonne("nombre", "Depenses", NOMBRE)
                .colonne("montant", "Montant", MONTANT)
                .colonne("part", "Part", POURCENT);
        var lignes = depenseService.parCategorie(debut, fin);
        BigDecimal total = lignes.stream().map(l -> l.total()).reduce(BigDecimal.ZERO, BigDecimal::add);
        long nombre = 0;
        for (var l : lignes) {
            r.ligne(l.categorie(), l.nombre(), l.total(), taux(l.total(), total));
            nombre += l.nombre();
        }
        return r.total("nombre", nombre).total("montant", total)
                .indicateur("Total des depenses", total, MONTANT)
                .indicateur("Categories", lignes.size(), NOMBRE)
                .construire();
    }

    /**
     * Compte de resultat simplifie : chiffre d'affaires, cout d'achat des
     * produits vendus, marge brute, depenses par categorie, benefice net. Les
     * paiements aux fournisseurs n'y figurent pas : la marchandise compte deja
     * dans le cout des ventes, au moment ou elle est vendue.
     */
    private Rapport resultat(Instant debut, Instant fin, ZoneId fuseau, String periode) {
        Rapport.Constructeur r = new Rapport.Constructeur("RESULTAT", "Compte de resultat", periode)
                .colonne("poste", "Poste", TEXTE)
                .colonne("montant", "Montant", MONTANT)
                .colonne("part", "Part du chiffre d'affaires", POURCENT);
        BigDecimal ca = venteRepository.totauxPeriode(debut, fin).stream()
                .map(l -> (BigDecimal) l[1]).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cout = venteRepository.coutAchatPeriode(debut, fin);
        BigDecimal marge = ca.subtract(cout);
        r.ligne("Chiffre d'affaires", ca, taux(ca, ca));
        r.ligne("Cout d'achat des produits vendus", cout.negate(), taux(cout, ca));
        r.ligne("Marge brute", marge, taux(marge, ca));
        var categories = depenseService.parCategorie(jour(debut, fuseau), jourFin(fin, fuseau));
        BigDecimal depenses = BigDecimal.ZERO;
        for (var c : categories) {
            r.ligne("   Depense : " + c.categorie(), c.total().negate(), taux(c.total(), ca));
            depenses = depenses.add(c.total());
        }
        r.ligne("Total des depenses", depenses.negate(), taux(depenses, ca));
        BigDecimal net = marge.subtract(depenses);
        r.ligne("Benefice net", net, taux(net, ca));
        return r.indicateur("Chiffre d'affaires", ca, MONTANT)
                .indicateur("Marge brute", marge, MONTANT)
                .indicateur("Depenses", depenses, MONTANT)
                .indicateur("Benefice net", net, MONTANT)
                .construire();
    }

    private static java.time.LocalDate jour(Instant debut, ZoneId fuseau) {
        return debut.atZone(fuseau).toLocalDate();
    }

    private static java.time.LocalDate jourFin(Instant fin, ZoneId fuseau) {
        return fin.minusNanos(1).atZone(fuseau).toLocalDate();
    }

    // ----------------------------------------------------------- caisses

    private Rapport caisses(Instant debut, Instant fin, String periode) {
        Rapport.Constructeur r = new Rapport.Constructeur("CAISSES", "Sessions de caisse", periode)
                .colonne("caisse", "Caisse", TEXTE)
                .colonne("session", "Session", TEXTE)
                .colonne("tenue", "Tenue par", TEXTE)
                .colonne("ouverture", "Ouverture", DATE_HEURE)
                .colonne("cloture", "Cloture", DATE_HEURE)
                .colonne("fond", "Fond de caisse", MONTANT)
                .colonne("commandes", "Commandes", NOMBRE)
                .colonne("ca", "Chiffre d'affaires", MONTANT)
                .colonne("attendu", "Especes attendues", MONTANT)
                .colonne("compte", "Especes comptees", MONTANT)
                .colonne("ecart", "Ecart", MONTANT)
                .colonne("statut", "Statut", TEXTE);
        BigDecimal ca = BigDecimal.ZERO;
        BigDecimal ecarts = BigDecimal.ZERO;
        long sessions = 0;
        for (SessionCaisse s : sessionRepository.findByOuverteLeGreaterThanEqualAndOuverteLeLessThanOrderByOuverteLeDesc(debut, fin)) {
            CaisseService.Reponse d = caisseService.rapport(s.getId());
            r.ligne(d.caisseNom(), d.numero(), d.caissierNom(), d.ouverteLe(), d.fermeeLe(), d.fondInitial(),
                    d.nombreVentes(), d.chiffreAffaires(),
                    d.montantTheorique() != null ? d.montantTheorique() : d.especesAttendues(),
                    d.montantCompte(), d.ecart(), d.statut() == SessionCaisse.Statut.OUVERTE ? "Ouverte" : "Fermee");
            ca = ca.add(d.chiffreAffaires());
            if (d.ecart() != null) {
                ecarts = ecarts.add(d.ecart());
            }
            sessions++;
        }
        return r.total("ca", ca).total("ecart", ecarts)
                .indicateur("Sessions", sessions, NOMBRE)
                .indicateur("Chiffre d'affaires", ca, MONTANT)
                .indicateur("Ecart cumule", ecarts, MONTANT)
                .construire();
    }

    // --------------------------------------------------------- restaurant

    private Rapport commandesTable(Instant debut, Instant fin, String periode) {
        Rapport.Constructeur r = new Rapport.Constructeur("COMMANDES_TABLE", "Commandes de table", periode)
                .colonne("numero", "Commande", TEXTE)
                .colonne("table", "Table / client", TEXTE)
                .colonne("serveur", "Serveur", TEXTE)
                .colonne("couverts", "Couverts", NOMBRE)
                .colonne("ouverture", "Ouverture", DATE_HEURE)
                .colonne("fermeture", "Fermeture", DATE_HEURE)
                .colonne("duree", "Duree (min)", NOMBRE)
                .colonne("statut", "Statut", TEXTE)
                .colonne("total", "Total", MONTANT);
        BigDecimal total = BigDecimal.ZERO;
        long couverts = 0;
        long encaissees = 0;
        long dureeCumul = 0;
        for (Commande c : commandeRepository.periode(debut, fin)) {
            RestaurationDto.CommandeResume resume = RestaurationDto.CommandeResume.de(c);
            Long duree = c.getFermeeLe() == null ? null : Duration.between(c.getOuverteLe(), c.getFermeeLe()).toMinutes();
            String statut = switch (c.getStatut()) {
                case OUVERTE -> "En cours";
                case ENCAISSEE -> "Encaissee";
                case ANNULEE -> "Annulee";
            };
            r.ligne(c.getNumero(), c.repere(), c.getServeurNom(), c.getCouverts(), c.getOuverteLe(), c.getFermeeLe(),
                    duree, statut, resume.total());
            if (c.getStatut() == Commande.Statut.ENCAISSEE) {
                total = total.add(resume.total());
                couverts += c.getCouverts() == null ? 0 : c.getCouverts();
                encaissees++;
                dureeCumul += duree == null ? 0 : duree;
            }
        }
        return r.total("total", total).total("couverts", couverts)
                .indicateur("Commandes encaissees", encaissees, NOMBRE)
                .indicateur("Couverts", couverts, NOMBRE)
                .indicateur("Moyenne par couvert", couverts == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(couverts), 0, RoundingMode.HALF_UP), MONTANT)
                .indicateur("Duree moyenne a table (min)", encaissees == 0 ? 0 : dureeCumul / encaissees, NOMBRE)
                .construire();
    }

    // ------------------------------------------------- ecrans de preparation

    private EcranPreparation ecran(Long id) {
        if (id == null) {
            throw new BusinessException("Choisissez l'ecran de preparation");
        }
        return ecranRepository.findById(id).orElseThrow(() -> new BusinessException("Ecran de preparation introuvable"));
    }

    private List<LigneCommande> lignesEcran(EcranPreparation ecran, Instant debut, Instant fin) {
        Set<Long> categories = ecran.getCategories().stream().map(c -> c.getId()).collect(Collectors.toSet());
        return categories.isEmpty() ? List.of() : ligneCommandeRepository.envoyeesParCategories(categories, debut, fin);
    }

    /**
     * Bons d'un ecran : un bon par envoi d'une commande, avec son detail, le
     * temps mis a le preparer et ou il en est.
     */
    private Rapport bonsEcran(EcranPreparation ecran, Instant debut, Instant fin, String periode) {
        Rapport.Constructeur r = new Rapport.Constructeur("PREPARATION_BONS", "Bons · " + ecran.getNom(), periode)
                .colonne("envoye", "Envoye le", DATE_HEURE)
                .colonne("commande", "Commande", TEXTE)
                .colonne("table", "Table / client", TEXTE)
                .colonne("serveur", "Serveur", TEXTE)
                .colonne("articles", "Articles", NOMBRE)
                .colonne("detail", "Detail", TEXTE)
                .colonne("pret", "Pret le", DATE_HEURE)
                .colonne("delai", "Preparation (min)", NOMBRE)
                .colonne("statut", "Statut", TEXTE);
        Map<String, List<LigneCommande>> parBon = new java.util.LinkedHashMap<>();
        for (LigneCommande l : lignesEcran(ecran, debut, fin)) {
            parBon.computeIfAbsent(l.getCommande().getId() + "/" + l.getEnvoi(), cle -> new java.util.ArrayList<>()).add(l);
        }
        BigDecimal articles = BigDecimal.ZERO;
        long delais = 0;
        long prepares = 0;
        long plusLong = 0;
        for (List<LigneCommande> bon : parBon.values()) {
            LigneCommande premiere = bon.get(0);
            List<LigneCommande> actives = bon.stream().filter(l -> l.getStatut() != LigneCommande.Statut.ANNULEE).toList();
            BigDecimal quantite = actives.stream().map(LigneCommande::getQuantite).reduce(BigDecimal.ZERO, BigDecimal::add);
            Instant pret = actives.isEmpty() || actives.stream().anyMatch(l -> l.getPreteLe() == null) ? null
                    : actives.stream().map(LigneCommande::getPreteLe).max(Comparator.naturalOrder()).orElse(null);
            Long delai = pret == null ? null : Duration.between(premiere.getEnvoyeeLe(), pret).toMinutes();
            r.ligne(premiere.getEnvoyeeLe(), premiere.getCommande().getNumero(), premiere.getCommande().repere(),
                    premiere.getCommande().getServeurNom(), quantite,
                    bon.stream().map(l -> quantiteTexte(l.getQuantite()) + " " + l.getLibelle()
                            + (l.getStatut() == LigneCommande.Statut.ANNULEE ? " (annule)" : "")).collect(Collectors.joining(", ")),
                    pret, delai, statutBon(actives));
            articles = articles.add(quantite);
            if (delai != null) {
                delais += delai;
                prepares++;
                plusLong = Math.max(plusLong, delai);
            }
        }
        return r.total("articles", articles)
                .indicateur("Bons", parBon.size(), NOMBRE)
                .indicateur("Articles prepares", articles, NOMBRE)
                .indicateur("Preparation moyenne (min)", prepares == 0 ? 0 : Math.round((double) delais / prepares), NOMBRE)
                .indicateur("Plus long bon (min)", plusLong, NOMBRE)
                .construire();
    }

    /** Produits d'un ecran : quantites preparees, bons, temps moyen et ce qu'ils representent. */
    private Rapport produitsEcran(EcranPreparation ecran, Instant debut, Instant fin, String periode) {
        Rapport.Constructeur r = new Rapport.Constructeur("PREPARATION_PRODUITS", "Produits · " + ecran.getNom(), periode)
                .colonne("produit", "Produit", TEXTE)
                .colonne("quantite", "Quantite preparee", NOMBRE)
                .colonne("bons", "Bons", NOMBRE)
                .colonne("delai", "Preparation moyenne (min)", NOMBRE)
                .colonne("annules", "Annules", NOMBRE)
                .colonne("montant", "Montant", MONTANT);
        Map<String, List<LigneCommande>> parProduit = new java.util.TreeMap<>();
        for (LigneCommande l : lignesEcran(ecran, debut, fin)) {
            parProduit.computeIfAbsent(l.getLibelle(), cle -> new java.util.ArrayList<>()).add(l);
        }
        BigDecimal quantiteTotale = BigDecimal.ZERO;
        BigDecimal montantTotal = BigDecimal.ZERO;
        List<Object[]> lignes = new java.util.ArrayList<>();
        for (Map.Entry<String, List<LigneCommande>> e : parProduit.entrySet()) {
            List<LigneCommande> actives = e.getValue().stream().filter(l -> l.getStatut() != LigneCommande.Statut.ANNULEE).toList();
            BigDecimal quantite = actives.stream().map(LigneCommande::getQuantite).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal annules = e.getValue().stream().filter(l -> l.getStatut() == LigneCommande.Statut.ANNULEE)
                    .map(LigneCommande::getQuantite).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal montant = actives.stream().map(l -> l.getPrixUnitaire().multiply(l.getQuantite()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long bons = e.getValue().stream().map(l -> l.getCommande().getId() + "/" + l.getEnvoi()).distinct().count();
            List<Long> delais = actives.stream().filter(l -> l.getPreteLe() != null)
                    .map(l -> Duration.between(l.getEnvoyeeLe(), l.getPreteLe()).toMinutes()).toList();
            Long moyenne = delais.isEmpty() ? null : Math.round(delais.stream().mapToLong(Long::longValue).average().orElse(0));
            lignes.add(new Object[]{e.getKey(), quantite, bons, moyenne, annules, montant});
            quantiteTotale = quantiteTotale.add(quantite);
            montantTotal = montantTotal.add(montant);
        }
        lignes.sort((a, b) -> ((BigDecimal) b[1]).compareTo((BigDecimal) a[1]));
        lignes.forEach(r::ligne);
        return r.total("quantite", quantiteTotale).total("montant", montantTotal)
                .indicateur("Produits", lignes.size(), NOMBRE)
                .indicateur("Quantite preparee", quantiteTotale, NOMBRE)
                .indicateur("Montant", montantTotal, MONTANT)
                .construire();
    }

    /** Etat d'un bon, du moins avance au plus avance. */
    private static String statutBon(List<LigneCommande> actives) {
        if (actives.isEmpty()) {
            return "Annule";
        }
        if (actives.stream().anyMatch(l -> l.getStatut() == LigneCommande.Statut.A_PREPARER)) {
            return "A preparer";
        }
        if (actives.stream().anyMatch(l -> l.getStatut() == LigneCommande.Statut.EN_PREPARATION)) {
            return "En preparation";
        }
        return actives.stream().allMatch(l -> l.getStatut() == LigneCommande.Statut.SERVIE) ? "Servi" : "Pret";
    }

    private static String quantiteTexte(BigDecimal quantite) {
        return quantite.stripTrailingZeros().toPlainString() + " ×";
    }

    private void exigerRestauration() {
        if (!restauration()) {
            throw new BusinessException("Ce rapport est reserve aux bars et restaurants");
        }
    }

    private boolean restauration() {
        return "RESTAURATION".equals(moduleService.variante(ContexteEntreprise.exiger(), ModuleApplicatif.POINT_DE_VENTE));
    }

    private static BigDecimal taux(BigDecimal marge, BigDecimal ca) {
        return ca.signum() == 0 ? BigDecimal.ZERO : marge.multiply(BigDecimal.valueOf(100)).divide(ca, 1, RoundingMode.HALF_UP);
    }
}
