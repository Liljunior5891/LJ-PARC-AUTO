package tg.parcauto.api.pointdevente.rapport;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;
import tg.parcauto.api.pointdevente.catalogue.CatalogueDto;
import tg.parcauto.api.pointdevente.catalogue.CatalogueService;
import tg.parcauto.api.pointdevente.catalogue.Categorie;
import tg.parcauto.api.pointdevente.catalogue.CategorieRepository;
import tg.parcauto.api.pointdevente.stock.StockService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static tg.parcauto.api.pointdevente.rapport.Rapport.Format.MONTANT;
import static tg.parcauto.api.pointdevente.rapport.Rapport.Format.NOMBRE;
import static tg.parcauto.api.pointdevente.rapport.Rapport.Format.TEXTE;

/**
 * Import des produits et des quantites de stock depuis un fichier CSV ou Excel.
 * <p>
 * Tout ou rien : chaque ligne est controlee, et une seule erreur suffit a ne
 * rien enregistrer ; le bilan les liste toutes, avec leur numero de ligne. En
 * simulation, rien n'est jamais enregistre : le bilan montre ce que l'import
 * ferait. Les modeles a telecharger reprennent les donnees actuelles, pour les
 * corriger dans un tableur puis les reimporter.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ImportService {

    private final ArticleRepository articleRepository;
    private final CategorieRepository categorieRepository;
    private final CatalogueService catalogueService;
    private final StockService stockService;

    public enum TypeImport { PRODUITS, STOCK }

    public record Erreur(int ligne, String message) {
    }

    public record Bilan(TypeImport type, int lignes, int crees, int modifies, int inchanges, boolean simulation,
                        boolean applique, List<Erreur> erreurs) {
    }

    /** Ligne de produit controlee, prete a creer ou a mettre a jour. */
    private record Produit(int ligne, Article existant, CatalogueDto.ArticleRequete requete, String categorie) {
    }

    public Bilan importer(TypeImport type, List<LecteurTableau.Ligne> lignes, boolean simulation) {
        if (lignes.isEmpty()) {
            throw new BusinessException("Aucune ligne a importer sous l'entete");
        }
        Bilan bilan = type == TypeImport.PRODUITS ? produits(lignes, simulation) : stock(lignes, simulation);
        if (!bilan.applique()) {
            // Simulation ou erreurs : la transaction n'enregistre rien, meme les categories creees en chemin.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return bilan;
    }

    // ---------------------------------------------------------- produits

    private Bilan produits(List<LecteurTableau.Ligne> lignes, boolean simulation) {
        List<Erreur> erreurs = new ArrayList<>();
        List<Produit> produits = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        int inchanges = 0;
        for (LecteurTableau.Ligne l : lignes) {
            try {
                String code = l.valeur("code", "reference", "ref");
                String nom = l.valeur("nom", "produit", "designation", "libelle");
                if (nom == null) {
                    throw new BusinessException("le nom du produit est obligatoire");
                }
                if (code != null && !codes.add(code.toUpperCase(Locale.ROOT))) {
                    throw new BusinessException("le code " + code + " figure deux fois dans le fichier");
                }
                String codeBarres = l.valeur("code_barres", "codebarres", "ean");
                Optional<Article> existant = code == null ? Optional.empty() : articleRepository.findByCodeIgnoreCase(code);
                if (existant.isEmpty() && codeBarres != null) {
                    existant = articleRepository.findByCodeBarres(codeBarres);
                }
                BigDecimal prixVente = nombre(l.valeur("prix_vente", "prix_de_vente", "prix"), "prix de vente");
                if (prixVente == null && existant.isPresent()) {
                    prixVente = existant.get().getPrixVente();
                }
                if (prixVente == null || prixVente.signum() < 0) {
                    throw new BusinessException("le prix de vente est obligatoire et positif");
                }
                Article a = existant.orElse(null);
                String suivi = l.valeur("suivi_stock", "suivi_du_stock");
                CatalogueDto.ArticleRequete requete = new CatalogueDto.ArticleRequete(
                        code, codeBarres != null ? codeBarres : a == null ? null : a.getCodeBarres(), nom,
                        null,
                        prixVente,
                        valeurOuExistant(nombre(l.valeur("prix_achat", "cout_d_achat", "cout_achat", "cout"), "prix d'achat"),
                                a == null ? null : a.getPrixAchat()),
                        Optional.ofNullable(l.valeur("unite")).orElse(a == null ? null : a.getUnite()),
                        suivi == null ? (a == null || a.isSuiviStock()) : oui(suivi),
                        a == null ? nombre(l.valeur("stock_initial", "stock"), "stock initial") : null,
                        valeurOuExistant(nombre(l.valeur("seuil_alerte", "seuil_d_alerte", "seuil"), "seuil d'alerte"),
                                a == null ? null : a.getSeuilAlerte()),
                        a == null ? null : a.isActif(),
                        a == null ? null : a.getImage());
                String categorie = l.valeur("categorie", "rayon", "famille");
                if (categorie == null && a != null && a.getCategorie() != null) {
                    categorie = a.getCategorie().getNom();
                }
                if (a != null && identique(a, requete, categorie)) {
                    inchanges++;
                    continue;
                }
                produits.add(new Produit(l.numero(), a, requete, categorie));
            } catch (BusinessException e) {
                erreurs.add(new Erreur(l.numero(), e.getMessage()));
            }
        }
        int crees = (int) produits.stream().filter(p -> p.existant() == null).count();
        int modifies = produits.size() - crees;
        if (!erreurs.isEmpty() || simulation) {
            return new Bilan(TypeImport.PRODUITS, lignes.size(), crees, modifies, inchanges, simulation, false, erreurs);
        }
        Map<String, Long> categories = new HashMap<>();
        categorieRepository.findAll().forEach(c -> categories.put(c.getNom().toLowerCase(Locale.ROOT), c.getId()));
        for (Produit p : produits) {
            try {
                Long categorieId = categorie(p.categorie(), categories);
                CatalogueDto.ArticleRequete r = p.requete();
                CatalogueDto.ArticleRequete avecCategorie = new CatalogueDto.ArticleRequete(r.code(), r.codeBarres(), r.nom(),
                        categorieId, r.prixVente(), r.prixAchat(), r.unite(), r.suiviStock(), r.stockInitial(),
                        r.seuilAlerte(), r.actif(), r.image());
                if (p.existant() == null) {
                    catalogueService.creer(avecCategorie);
                } else {
                    catalogueService.modifier(p.existant().getId(), avecCategorie);
                }
            } catch (BusinessException e) {
                erreurs.add(new Erreur(p.ligne(), e.getMessage()));
            }
        }
        boolean applique = erreurs.isEmpty();
        return new Bilan(TypeImport.PRODUITS, lignes.size(), applique ? crees : 0, applique ? modifies : 0,
                applique ? inchanges : 0, false, applique, erreurs);
    }

    /** Vrai si la ligne reprend le produit tel qu'il est : il n'est alors ni reecrit ni compte comme modifie. */
    private static boolean identique(Article a, CatalogueDto.ArticleRequete r, String categorie) {
        String categorieActuelle = a.getCategorie() == null ? null : a.getCategorie().getNom();
        return Objects.equals(a.getNom(), r.nom())
                && Objects.equals(vide(a.getCodeBarres()), vide(r.codeBarres()))
                && Objects.equals(vide(a.getUnite()), vide(r.unite()))
                && a.isSuiviStock() == r.suiviStock()
                && egal(a.getPrixVente(), r.prixVente())
                && egal(a.getPrixAchat(), r.prixAchat())
                && egal(a.getSeuilAlerte(), r.seuilAlerte())
                && (categorie == null ? categorieActuelle == null : categorie.equalsIgnoreCase(categorieActuelle));
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private static boolean egal(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    /** Categorie par son nom, creee si elle n'existe pas encore. */
    private Long categorie(String nom, Map<String, Long> categories) {
        if (nom == null || nom.isBlank()) {
            return null;
        }
        String cle = nom.trim().toLowerCase(Locale.ROOT);
        Long id = categories.get(cle);
        if (id == null) {
            id = catalogueService.creerCategorie(new CatalogueDto.CategorieRequete(nom.trim(), null,
                    categories.size(), true, null, null)).id();
            categories.put(cle, id);
        }
        return id;
    }

    // ------------------------------------------------------------- stock

    private Bilan stock(List<LecteurTableau.Ligne> lignes, boolean simulation) {
        List<Erreur> erreurs = new ArrayList<>();
        List<StockService.Comptage> comptages = new ArrayList<>();
        int inchanges = 0;
        Set<Long> vus = new HashSet<>();
        for (LecteurTableau.Ligne l : lignes) {
            try {
                String code = l.valeur("code", "reference", "ref");
                String codeBarres = l.valeur("code_barres", "codebarres", "ean");
                Optional<Article> article = code == null ? Optional.empty() : articleRepository.findByCodeIgnoreCase(code);
                if (article.isEmpty() && codeBarres != null) {
                    article = articleRepository.findByCodeBarres(codeBarres);
                }
                Article a = article.orElseThrow(() -> new BusinessException(
                        "produit introuvable" + (code != null ? " (code " + code + ")" : " : indiquez son code")));
                if (!a.isSuiviStock()) {
                    throw new BusinessException("le stock de " + a.getNom() + " n'est pas suivi");
                }
                BigDecimal quantite = nombre(l.valeur("quantite", "quantite_comptee", "stock", "stock_compte"), "quantite");
                if (quantite == null || quantite.signum() < 0) {
                    throw new BusinessException("la quantite comptee est obligatoire et positive");
                }
                if (!vus.add(a.getId())) {
                    throw new BusinessException(a.getNom() + " figure deux fois dans le fichier");
                }
                if (a.getStockActuel().compareTo(quantite) == 0) {
                    inchanges++;
                }
                comptages.add(new StockService.Comptage(a.getId(), quantite));
            } catch (BusinessException e) {
                erreurs.add(new Erreur(l.numero(), e.getMessage()));
            }
        }
        int corriges = comptages.size() - inchanges;
        if (!erreurs.isEmpty() || simulation || comptages.isEmpty()) {
            return new Bilan(TypeImport.STOCK, lignes.size(), 0, corriges, inchanges, simulation, false, erreurs);
        }
        stockService.inventaire(new StockService.Inventaire(comptages, "Import de stock"));
        return new Bilan(TypeImport.STOCK, lignes.size(), 0, corriges, inchanges, false, true, erreurs);
    }

    // ------------------------------------------------------------ modeles

    /** Modele d'import pre-rempli avec les donnees actuelles. */
    @Transactional(readOnly = true)
    public Rapport modele(TypeImport type) {
        List<Article> articles = articleRepository.findAll().stream()
                .filter(Article::isActif)
                .sorted(Comparator.comparing(a -> a.getNom().toLowerCase(Locale.ROOT)))
                .toList();
        if (type == TypeImport.STOCK) {
            Rapport.Constructeur r = new Rapport.Constructeur("MODELE_STOCK", "Import du stock compte",
                    "Saisissez la quantite comptee pour chaque produit, puis importez le fichier")
                    .colonne("code", "Code", TEXTE)
                    .colonne("nom", "Nom", TEXTE)
                    .colonne("quantite", "Quantite", NOMBRE);
            articles.stream().filter(Article::isSuiviStock).forEach(a -> r.ligne(a.getCode(), a.getNom(), a.getStockActuel()));
            return r.construire();
        }
        Rapport.Constructeur r = new Rapport.Constructeur("MODELE_PRODUITS", "Import des produits",
                "Une ligne par produit ; un code existant met le produit a jour, un code vide en cree un")
                .colonne("code", "Code", TEXTE)
                .colonne("nom", "Nom", TEXTE)
                .colonne("categorie", "Categorie", TEXTE)
                .colonne("prix_vente", "Prix vente", MONTANT)
                .colonne("prix_achat", "Prix achat", MONTANT)
                .colonne("code_barres", "Code barres", TEXTE)
                .colonne("unite", "Unite", TEXTE)
                .colonne("suivi_stock", "Suivi stock", TEXTE)
                .colonne("seuil_alerte", "Seuil alerte", NOMBRE)
                .colonne("stock_initial", "Stock initial", NOMBRE);
        for (Article a : articles) {
            Categorie c = a.getCategorie();
            r.ligne(a.getCode(), a.getNom(), c == null ? null : c.getNom(), a.getPrixVente(), a.getPrixAchat(),
                    a.getCodeBarres(), a.getUnite(), a.isSuiviStock() ? "oui" : "non", a.getSeuilAlerte(), null);
        }
        return r.construire();
    }

    // ----------------------------------------------------------- interne

    private static BigDecimal valeurOuExistant(BigDecimal valeur, BigDecimal existant) {
        return valeur != null ? valeur : existant;
    }

    private static boolean oui(String valeur) {
        String v = valeur.trim().toLowerCase(Locale.ROOT);
        return v.equals("oui") || v.equals("o") || v.equals("1") || v.equals("vrai") || v.equals("true") || v.equals("yes");
    }

    /** Nombre ecrit a la francaise ou a l'anglaise : 3 500,50 / 3500.5 / 3 500 F. */
    private static BigDecimal nombre(String valeur, String champ) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        String nettoye = valeur.replaceAll("[\\s  FCFAfcfa]", "");
        if (nettoye.contains(",") && nettoye.contains(".")) {
            nettoye = nettoye.replace(".", "").replace(',', '.');
        } else {
            nettoye = nettoye.replace(',', '.');
        }
        try {
            return new BigDecimal(nettoye);
        } catch (NumberFormatException e) {
            throw new BusinessException(champ + " invalide : « " + valeur + " »");
        }
    }
}
