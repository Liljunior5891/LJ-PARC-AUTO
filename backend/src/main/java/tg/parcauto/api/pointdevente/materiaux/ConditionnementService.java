package tg.parcauto.api.pointdevente.materiaux;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;
import tg.parcauto.api.pointdevente.catalogue.Conditionnement;
import tg.parcauto.api.pointdevente.catalogue.ConditionnementRepository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Conditionnements des produits : la tonne de ciment, la botte de fer, le
 * carton de carreaux. Un conditionnement deja vendu n'est jamais supprime,
 * seulement retire de la vente, pour que les tickets passes restent lisibles.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ConditionnementService {

    private final ConditionnementRepository repository;
    private final ArticleRepository articleRepository;
    private final ReglageMateriauxService reglages;

    /** Tous les conditionnements vendables, charges une fois par la caisse. */
    @Transactional(readOnly = true)
    public List<MateriauxDto.ConditionnementReponse> vendables() {
        if (!reglages.exiger().isConditionnementsActifs()) {
            return List.of();
        }
        return repository.vendables().stream().map(MateriauxDto.ConditionnementReponse::de).toList();
    }

    @Transactional(readOnly = true)
    public List<MateriauxDto.ConditionnementReponse> duProduit(Long articleId) {
        reglages.exiger();
        return repository.findByArticleIdOrderByCoefficientAsc(articleId).stream()
                .map(MateriauxDto.ConditionnementReponse::de).toList();
    }

    /**
     * Remplace les conditionnements d'un produit par la liste recue : ceux qui
     * ont un identifiant sont mis a jour, les nouveaux crees, les absents
     * retires de la vente (jamais effaces).
     */
    public List<MateriauxDto.ConditionnementReponse> enregistrer(Long articleId,
                                                                 List<MateriauxDto.ConditionnementRequete> demandes) {
        if (!reglages.exiger().isConditionnementsActifs()) {
            throw new BusinessException("La vente par conditionnement est desactivee dans les reglages");
        }
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> ResourceNotFoundException.of("Article", articleId));
        Map<Long, Conditionnement> existants = new HashMap<>();
        repository.findByArticleIdOrderByCoefficientAsc(articleId).forEach(c -> existants.put(c.getId(), c));

        Set<String> noms = new HashSet<>();
        Set<Long> gardes = new HashSet<>();
        for (MateriauxDto.ConditionnementRequete d : demandes) {
            String nom = d.nom().trim();
            if (!noms.add(nom.toLowerCase())) {
                throw new BusinessException("Le conditionnement " + nom + " figure deux fois");
            }
            Conditionnement c;
            if (d.id() != null) {
                c = existants.get(d.id());
                if (c == null) {
                    throw new BusinessException("Ce conditionnement n'appartient pas a " + article.getNom());
                }
                gardes.add(c.getId());
            } else {
                c = new Conditionnement();
                c.setArticle(article);
            }
            String codeBarres = d.codeBarres() == null || d.codeBarres().isBlank() ? null : d.codeBarres().trim();
            verifierCodeBarres(codeBarres, c.getId(), article);
            c.setNom(nom);
            c.setCoefficient(d.coefficient());
            c.setPrixVente(d.prixVente());
            c.setCodeBarres(codeBarres);
            c.setActif(d.actif() == null || d.actif());
            repository.save(c);
        }
        existants.values().stream().filter(c -> !gardes.contains(c.getId())).forEach(c -> c.setActif(false));
        return duProduit(articleId);
    }

    /** Un code-barres designe un seul produit ou conditionnement. */
    private void verifierCodeBarres(String codeBarres, Long idCourant, Article article) {
        if (codeBarres == null) {
            return;
        }
        articleRepository.findByCodeBarres(codeBarres).ifPresent(a -> {
            throw new BusinessException("Le code-barres " + codeBarres + " est deja celui du produit " + a.getNom());
        });
        repository.findByCodeBarres(codeBarres)
                .filter(autre -> !autre.getId().equals(idCourant))
                .ifPresent(autre -> {
                    throw new BusinessException("Le code-barres " + codeBarres + " est deja celui de "
                            + autre.getArticle().getNom() + " (" + autre.getNom() + ")");
                });
    }
}
