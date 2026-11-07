package tg.parcauto.api.pointdevente.associe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;
import tg.parcauto.api.pointdevente.catalogue.Categorie;
import tg.parcauto.api.pointdevente.catalogue.CategorieRepository;
import tg.parcauto.api.pointdevente.reglage.ReglagePdvService;
import tg.parcauto.api.pointdevente.vente.VenteRepository;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.Utilisateur;
import tg.parcauto.api.security.UtilisateurRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Benefices des associes : leurs rattachements (produit ou categorie, avec un
 * pourcentage) et leur part de la marge brute des ventes validees de la periode.
 * <p>
 * Sur un produit, le taux d'un associe est celui de son rattachement direct, a
 * defaut celui de la categorie du produit. Les taux de tous les associes sur un
 * meme produit ne depassent jamais 100 % : le reste revient a l'entreprise.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AssocieService {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private final AssociePartRepository repository;
    private final UtilisateurRepository utilisateurs;
    private final ArticleRepository articleRepository;
    private final CategorieRepository categorieRepository;
    private final VenteRepository venteRepository;
    private final ReglagePdvService reglages;

    // ------------------------------------------------------------------ dto

    public record PartRequete(Long articleId, Long categorieId,
                              @NotNull @Positive @DecimalMax("100") BigDecimal taux) {
    }

    public record Parts(@NotNull List<@Valid PartRequete> parts) {
    }

    public record PartReponse(Long id, Long articleId, String articleNom, Long categorieId, String categorieNom,
                              BigDecimal taux) {

        static PartReponse de(AssociePart p) {
            return new PartReponse(p.getId(),
                    p.getArticle() == null ? null : p.getArticle().getId(),
                    p.getArticle() == null ? null : p.getArticle().getNom(),
                    p.getCategorie() == null ? null : p.getCategorie().getId(),
                    p.getCategorie() == null ? null : p.getCategorie().getNom(), p.getTaux());
        }
    }

    public record Associe(Long id, String nom, String email, boolean actif, List<PartReponse> parts) {
    }

    public record LigneBenefice(Long articleId, String produit, String categorie, BigDecimal quantite,
                                BigDecimal chiffreAffaires, BigDecimal cout, BigDecimal marge, BigDecimal taux,
                                BigDecimal part, String origine) {
    }

    public record BeneficeAssocie(Long associeId, String nom, String email, BigDecimal chiffreAffaires,
                                  BigDecimal marge, BigDecimal part, List<LigneBenefice> lignes) {
    }

    /**
     * Vue d'ensemble pour l'administrateur ; pour un associe (vueAssocie), seulement
     * ses produits et sa part, sans celles des autres ni la part de l'entreprise.
     */
    public record Benefices(Instant debut, Instant fin, BigDecimal chiffreAffaires, BigDecimal margeBrute,
                            BigDecimal partsAssocies, BigDecimal partEntreprise, List<BeneficeAssocie> associes,
                            boolean vueAssocie) {
    }

    // ----------------------------------------------------------- associes

    @Transactional(readOnly = true)
    public List<Associe> associes() {
        Map<Long, List<PartReponse>> parts = repository.toutes().stream()
                .collect(Collectors.groupingBy(AssociePart::getAssocieId,
                        Collectors.mapping(PartReponse::de, Collectors.toList())));
        return comptesAssocies().stream()
                .map(u -> new Associe(u.getId(), u.nomComplet(), u.getEmail(), u.isActif(),
                        parts.getOrDefault(u.getId(), List.of())))
                .toList();
    }

    /** Remplace les rattachements d'un associe, apres controle des 100 % par produit. */
    public Associe remplacerParts(Long associeId, List<PartRequete> demandes) {
        reglages.exigerAssocies();
        Utilisateur associe = comptesAssocies().stream().filter(u -> u.getId().equals(associeId)).findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Associe", associeId));
        List<AssociePart> nouvelles = new ArrayList<>();
        Set<String> vues = new HashSet<>();
        for (PartRequete d : demandes) {
            if ((d.articleId() == null) == (d.categorieId() == null)) {
                throw new BusinessException("Chaque ligne vise soit un produit, soit une categorie");
            }
            if (!vues.add(d.articleId() != null ? "a" + d.articleId() : "c" + d.categorieId())) {
                throw new BusinessException("Un produit ou une categorie figure deux fois pour cet associe");
            }
            AssociePart p = new AssociePart();
            p.setAssocieId(associeId);
            if (d.articleId() != null) {
                p.setArticle(articleRepository.findById(d.articleId())
                        .orElseThrow(() -> ResourceNotFoundException.of("Produit", d.articleId())));
            } else {
                p.setCategorie(categorieRepository.findById(d.categorieId())
                        .orElseThrow(() -> ResourceNotFoundException.of("Categorie", d.categorieId())));
            }
            p.setTaux(d.taux().setScale(2, RoundingMode.HALF_UP));
            nouvelles.add(p);
        }
        List<AssociePart> autres = repository.toutes().stream()
                .filter(p -> !p.getAssocieId().equals(associeId)).toList();
        verifierTotaux(autres, nouvelles);
        repository.deleteAll(repository.findByAssocieId(associeId));
        repository.flush();
        repository.saveAll(nouvelles);
        repository.flush();
        return new Associe(associe.getId(), associe.nomComplet(), associe.getEmail(), associe.isActif(),
                nouvelles.stream().map(PartReponse::de).toList());
    }

    // ---------------------------------------------------------- benefices

    /** L'administrateur voit tous les associes ; un associe ne voit que lui-meme. */
    @Transactional(readOnly = true)
    public Benefices benefices(Instant debut, Instant fin, tg.parcauto.api.security.UtilisateurPrincipal lecteur) {
        Benefices tout = benefices(debut, fin);
        boolean admin = lecteur.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(Role.ADMIN.authority()));
        if (admin) {
            return tout;
        }
        BeneficeAssocie moi = tout.associes().stream().filter(a -> a.associeId().equals(lecteur.getId())).findFirst()
                .orElse(new BeneficeAssocie(lecteur.getId(), lecteur.getNomComplet(), null, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, List.of()));
        return new Benefices(debut, fin, moi.chiffreAffaires(), moi.marge(), moi.part(), null, List.of(moi), true);
    }

    @Transactional(readOnly = true)
    public Benefices benefices(Instant debut, Instant fin) {
        reglages.exigerAssocies();
        List<Object[]> ventes = venteRepository.meilleursArticles(debut, fin, Pageable.unpaged());
        Map<Long, Article> articles = articleRepository.findAllById(
                        ventes.stream().map(l -> (Long) l[0]).filter(java.util.Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(Article::getId, Function.identity()));
        List<AssociePart> parts = repository.toutes();
        BigDecimal ca = BigDecimal.ZERO;
        BigDecimal marge = BigDecimal.ZERO;
        BigDecimal totalParts = BigDecimal.ZERO;
        List<BeneficeAssocie> resultat = new ArrayList<>();
        Map<Long, List<LigneBenefice>> parAssocie = new HashMap<>();
        for (Object[] l : ventes) {
            BigDecimal caLigne = (BigDecimal) l[3];
            BigDecimal cout = (BigDecimal) l[4];
            BigDecimal margeLigne = caLigne.subtract(cout);
            ca = ca.add(caLigne);
            marge = marge.add(margeLigne);
            Article article = l[0] == null ? null : articles.get((Long) l[0]);
            if (article == null) {
                continue;
            }
            for (Map.Entry<Long, AssociePart> e : tauxEffectifs(parts, article).entrySet()) {
                AssociePart p = e.getValue();
                BigDecimal part = margeLigne.multiply(p.getTaux()).divide(CENT, 2, RoundingMode.HALF_UP);
                totalParts = totalParts.add(part);
                parAssocie.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(new LigneBenefice(
                        article.getId(), (String) l[1],
                        article.getCategorie() == null ? null : article.getCategorie().getNom(),
                        (BigDecimal) l[2], caLigne, cout, margeLigne, p.getTaux(), part,
                        p.getArticle() != null ? "Produit" : "Categorie " + p.getCategorie().getNom()));
            }
        }
        for (Utilisateur u : comptesAssocies()) {
            List<LigneBenefice> lignes = parAssocie.getOrDefault(u.getId(), List.of());
            resultat.add(new BeneficeAssocie(u.getId(), u.nomComplet(), u.getEmail(),
                    somme(lignes, LigneBenefice::chiffreAffaires), somme(lignes, LigneBenefice::marge),
                    somme(lignes, LigneBenefice::part),
                    lignes.stream().sorted(Comparator.comparing(LigneBenefice::part).reversed()).toList()));
        }
        resultat.sort(Comparator.comparing(BeneficeAssocie::part).reversed());
        return new Benefices(debut, fin, ca, marge, totalParts, marge.subtract(totalParts), resultat, false);
    }

    // ------------------------------------------------------------ internes

    /** Comptes de l'entreprise au profil ASSOCIE. */
    private List<Utilisateur> comptesAssocies() {
        return utilisateurs.findByEntrepriseIdOrderByNomAscPrenomAsc(ContexteEntreprise.exiger()).stream()
                .filter(u -> u.getRoles().contains(Role.ASSOCIE))
                .toList();
    }

    /** Taux de chaque associe sur un produit : rattachement direct, sinon par la categorie. */
    static Map<Long, AssociePart> tauxEffectifs(List<AssociePart> parts, Article article) {
        Map<Long, AssociePart> taux = new HashMap<>();
        Long categorie = article.getCategorie() == null ? null : article.getCategorie().getId();
        for (AssociePart p : parts) {
            if (p.getCategorie() != null && p.getCategorie().getId().equals(categorie)) {
                taux.putIfAbsent(p.getAssocieId(), p);
            }
        }
        for (AssociePart p : parts) {
            if (p.getArticle() != null && p.getArticle().getId().equals(article.getId())) {
                taux.put(p.getAssocieId(), p);
            }
        }
        return taux;
    }

    /** Aucun produit ne doit depasser 100 % en cumulant les associes. */
    private void verifierTotaux(List<AssociePart> autres, List<AssociePart> nouvelles) {
        List<AssociePart> toutes = new ArrayList<>(autres);
        toutes.addAll(nouvelles);
        Set<Long> categories = new HashSet<>();
        Set<Long> produits = new HashSet<>();
        for (AssociePart p : toutes) {
            if (p.getCategorie() != null) {
                categories.add(p.getCategorie().getId());
            } else {
                produits.add(p.getArticle().getId());
            }
        }
        List<Article> concernes = articleRepository.findAll().stream()
                .filter(a -> produits.contains(a.getId())
                        || (a.getCategorie() != null && categories.contains(a.getCategorie().getId())))
                .toList();
        for (Article a : concernes) {
            BigDecimal total = tauxEffectifs(toutes, a).values().stream()
                    .map(AssociePart::getTaux).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(CENT) > 0) {
                throw new BusinessException("Le produit " + a.getNom() + " depasserait 100 % de parts ("
                        + total.stripTrailingZeros().toPlainString() + " %) en cumulant les associes");
            }
        }
    }

    private static BigDecimal somme(List<LigneBenefice> lignes, Function<LigneBenefice, BigDecimal> champ) {
        return lignes.stream().map(champ).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
