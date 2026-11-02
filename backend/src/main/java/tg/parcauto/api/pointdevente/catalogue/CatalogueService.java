package tg.parcauto.api.pointdevente.catalogue;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.stock.StockService;
import tg.parcauto.api.pointdevente.stock.TypeMouvementStock;

import java.util.List;

/** Categories et articles du point de vente. */
@Service
@RequiredArgsConstructor
@Transactional
public class CatalogueService {

    private final CategorieRepository categorieRepository;
    private final ArticleRepository articleRepository;
    private final StockService stockService;
    private final NumerotationService numerotation;

    // ----------------------------------------------------------- categories

    @Transactional(readOnly = true)
    public List<CatalogueDto.CategorieReponse> categories() {
        return categorieRepository.findAllByOrderByOrdreAscNomAsc().stream()
                .map(CatalogueDto.CategorieReponse::de)
                .toList();
    }

    public CatalogueDto.CategorieReponse creerCategorie(CatalogueDto.CategorieRequete r) {
        if (categorieRepository.existsByNomIgnoreCase(r.nom().trim())) {
            throw new BusinessException("Une categorie porte deja le nom " + r.nom().trim());
        }
        Categorie categorie = new Categorie();
        appliquer(categorie, r);
        return CatalogueDto.CategorieReponse.de(categorieRepository.save(categorie));
    }

    public CatalogueDto.CategorieReponse modifierCategorie(Long id, CatalogueDto.CategorieRequete r) {
        Categorie categorie = chargerCategorie(id);
        appliquer(categorie, r);
        return CatalogueDto.CategorieReponse.de(categorie);
    }

    // ------------------------------------------------------------- articles

    @Transactional(readOnly = true)
    public Page<CatalogueDto.ArticleReponse> rechercher(String q, Long categorieId, Boolean actif, Boolean stockBas,
                                                        Boolean enLigne, Pageable pageable) {
        return articleRepository.findAll(ArticleSpecifications.rechercher(q, categorieId, actif, stockBas, enLigne),
                        pageable)
                .map(CatalogueDto.ArticleReponse::de);
    }

    /** Catalogue complet des articles actifs, charge une fois par l'ecran de caisse. */
    @Transactional(readOnly = true)
    public List<CatalogueDto.ArticleReponse> catalogueCaisse() {
        return articleRepository.catalogueCaisse().stream().map(CatalogueDto.ArticleReponse::de).toList();
    }

    @Transactional(readOnly = true)
    public CatalogueDto.ArticleReponse parId(Long id) {
        return CatalogueDto.ArticleReponse.de(charger(id));
    }

    /** Lecture de la douchette : code-barres d'abord, code interne ensuite. */
    @Transactional(readOnly = true)
    public CatalogueDto.ArticleReponse parCode(String code) {
        String valeur = code.trim();
        return articleRepository.findByCodeBarres(valeur)
                .or(() -> articleRepository.findByCodeIgnoreCase(valeur))
                .filter(Article::isActif)
                .map(CatalogueDto.ArticleReponse::de)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun article actif pour le code " + valeur));
    }

    public CatalogueDto.ArticleReponse creer(CatalogueDto.ArticleRequete r) {
        String code = r.code() == null || r.code().isBlank()
                ? numerotation.prochainNumero("ART")
                : r.code().trim().toUpperCase();
        if (articleRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Un article existe deja avec le code " + code);
        }
        verifierCodeBarresLibre(r.codeBarres(), null);
        Article article = new Article();
        article.setCode(code);
        appliquer(article, r);
        article = articleRepository.save(article);

        if (article.isSuiviStock() && r.stockInitial() != null && r.stockInitial().signum() > 0) {
            stockService.appliquer(article, TypeMouvementStock.ENTREE, r.stockInitial(), article.getPrixAchat(),
                    "Stock initial", null);
        }
        return CatalogueDto.ArticleReponse.de(article);
    }

    /** Le stock ne se modifie pas ici : il passe par un mouvement, pour rester trace. */
    public CatalogueDto.ArticleReponse modifier(Long id, CatalogueDto.ArticleRequete r) {
        Article article = charger(id);
        verifierCodeBarresLibre(r.codeBarres(), id);
        appliquer(article, r);
        return CatalogueDto.ArticleReponse.de(article);
    }

    /** Archivage : l'article quitte la caisse, ses ventes passees restent intactes. */
    public void archiver(Long id) {
        charger(id).setActif(false);
    }

    private void appliquer(Categorie c, CatalogueDto.CategorieRequete r) {
        c.setNom(r.nom().trim());
        c.setCouleur(r.couleur());
        if (r.ordre() != null) {
            c.setOrdre(r.ordre());
        }
        if (r.actif() != null) {
            c.setActif(r.actif());
        }
        if (r.poste() != null) {
            c.setPoste(r.poste());
        }
        c.setIcone(r.icone() == null || r.icone().isBlank() ? null : r.icone().trim());
    }

    private void appliquer(Article a, CatalogueDto.ArticleRequete r) {
        a.setNom(r.nom().trim());
        a.setCodeBarres(r.codeBarres() == null || r.codeBarres().isBlank() ? null : r.codeBarres().trim());
        a.setCategorie(r.categorieId() == null ? null : chargerCategorie(r.categorieId()));
        a.setPrixVente(r.prixVente());
        a.setImage(r.image() == null || r.image().isBlank() ? null : r.image());
        a.setPrixAchat(r.prixAchat());
        a.setUnite(r.unite() == null || r.unite().isBlank() ? null : r.unite().trim());
        a.setSeuilAlerte(r.seuilAlerte());
        if (r.suiviStock() != null) {
            a.setSuiviStock(r.suiviStock());
        }
        if (r.actif() != null) {
            a.setActif(r.actif());
        }
    }

    private void verifierCodeBarresLibre(String codeBarres, Long idCourant) {
        if (codeBarres == null || codeBarres.isBlank()) {
            return;
        }
        articleRepository.findByCodeBarres(codeBarres.trim())
                .filter(autre -> !autre.getId().equals(idCourant))
                .ifPresent(autre -> {
                    throw new BusinessException("Le code-barres " + codeBarres.trim()
                            + " est deja attribue a " + autre.getNom());
                });
    }

    private Article charger(Long id) {
        return articleRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Article", id));
    }

    private Categorie chargerCategorie(Long id) {
        return categorieRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Categorie", id));
    }
}
