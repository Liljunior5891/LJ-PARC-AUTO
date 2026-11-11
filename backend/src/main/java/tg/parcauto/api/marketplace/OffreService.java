package tg.parcauto.api.marketplace;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.parc.camion.Camion;
import tg.parcauto.api.parc.camion.CamionRepository;
import tg.parcauto.api.parc.camion.StatutCamion;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleService;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Offres de la boutique : articles du point de vente, vehicules du parc a louer
 * et produits libres. Prix, photo et stock d'un article sont lus en direct
 * dans le point de vente, sauf prix ou photo propres a la boutique.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OffreService {

    /** Quantite commandable affichee dans la vitrine publique, au plus. */
    static final int QUANTITE_MAX_AFFICHEE = 20;


    /** Un vehicule dans l'un de ces etats ne peut plus etre loue. */
    private static final Set<StatutCamion> HORS_LOCATION =
            EnumSet.of(StatutCamion.VENDU, StatutCamion.HORS_SERVICE, StatutCamion.ACCIDENTE);

    private final OffreRepository repository;
    private final ArticleRepository articleRepository;
    private final CamionRepository camionRepository;
    private final ModuleService moduleService;
    private final OffrePhotoRepository photoRepository;
    private final tg.parcauto.api.pointdevente.lieu.LieuStockService lieux;

    // ------------------------------------------------------------ Espace de gestion

    @Transactional(readOnly = true)
    public List<MarketplaceDto.OffreReponse> lister() {
        List<Offre> offres = repository.findAllByOrderByMiseEnAvantDescOrdreAscTitreAsc();
        Sources sources = sourcesDe(offres);
        return offres.stream().map(o -> versReponse(o, sources)).toList();
    }

    /** Ce qui peut etre publie : articles actifs du point de vente et vehicules du parc. */
    @Transactional(readOnly = true)
    public MarketplaceDto.Sources sources() {
        Long entreprise = ContexteEntreprise.exiger();
        boolean pdv = moduleService.estActif(entreprise, ModuleApplicatif.POINT_DE_VENTE);
        boolean parc = moduleService.estActif(entreprise, ModuleApplicatif.GESTION_PARC);
        List<Offre> offres = repository.findAll();
        Set<Long> articlesPublies = offres.stream().map(Offre::getArticleId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> camionsPublies = offres.stream().map(Offre::getCamionId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<MarketplaceDto.SourceArticle> articles = !pdv ? List.of() : articleRepository.findAll().stream()
                .filter(Article::isActif)
                .sorted(Comparator.comparing(a -> a.getNom().toLowerCase()))
                .map(a -> new MarketplaceDto.SourceArticle(a.getId(), a.getCode(), a.getNom(),
                        a.getCategorie() == null ? null : a.getCategorie().getNom(), a.getPrixVente(), a.getImage(),
                        a.isSuiviStock(), a.stockDisponible(), articlesPublies.contains(a.getId())))
                .toList();
        List<MarketplaceDto.SourceVehicule> vehicules = !parc ? List.of() : camionRepository.findAll().stream()
                .filter(c -> c.isActif() && !HORS_LOCATION.contains(c.getStatut()))
                .sorted(Comparator.comparing(Camion::getImmatriculation))
                .map(c -> new MarketplaceDto.SourceVehicule(c.getId(), c.getImmatriculation(), libelleVehicule(c),
                        c.getType(), c.getPhotoUrl(), c.getStatut().getLibelle(), camionsPublies.contains(c.getId())))
                .toList();
        return new MarketplaceDto.Sources(pdv, parc, articles, vehicules);
    }

    public MarketplaceDto.OffreReponse creer(MarketplaceDto.OffreRequete r) {
        Offre offre = new Offre();
        appliquer(offre, r);
        return versReponse(repository.save(offre), sourcesDe(List.of(offre)));
    }

    public MarketplaceDto.OffreReponse modifier(Long id, MarketplaceDto.OffreRequete r) {
        Offre offre = charger(id);
        if (offre.getType() != r.type()) {
            throw new BusinessException("Le type d'une offre ne change pas : retirez-la et publiez-en une autre");
        }
        appliquer(offre, r);
        return versReponse(repository.save(offre), sourcesDe(List.of(offre)));
    }

    /** Publie plusieurs articles du point de vente, au prix et a la photo du point de vente. */
    public List<MarketplaceDto.OffreReponse> publierArticles(List<Long> articleIds) {
        Set<Long> dejaPublies = new HashSet<>();
        repository.findAll().forEach(o -> {
            if (o.getArticleId() != null) {
                dejaPublies.add(o.getArticleId());
            }
        });
        List<Offre> creees = articleRepository.findAllById(articleIds).stream()
                .filter(a -> a.isActif() && !dejaPublies.contains(a.getId()))
                .map(a -> {
                    Offre offre = new Offre();
                    offre.setType(Offre.Type.ARTICLE);
                    offre.setArticleId(a.getId());
                    offre.setTitre(a.getNom());
                    offre.setCategorie(a.getCategorie() == null ? null : a.getCategorie().getNom());
                    offre.setUnitePrix(a.getUnite());
                    return repository.save(offre);
                })
                .toList();
        Sources sources = sourcesDe(creees);
        return creees.stream().map(o -> versReponse(o, sources)).toList();
    }

    /** Articles du point de vente publies (ou masques) dans la boutique en ligne. */
    @Transactional(readOnly = true)
    public List<MarketplaceDto.ArticleEnLigne> articlesEnLigne() {
        return repository.findAll().stream()
                .filter(o -> o.getType() == Offre.Type.ARTICLE && o.getArticleId() != null)
                .map(o -> new MarketplaceDto.ArticleEnLigne(o.getArticleId(), o.getId(), o.isPubliee()))
                .toList();
    }

    /**
     * Met un article du catalogue en vente en ligne ou l'en retire, depuis la
     * liste des produits : l'offre est creee au premier passage en ligne.
     */
    public MarketplaceDto.ArticleEnLigne publierArticle(Long articleId, boolean publiee) {
        return publierArticle(articleId, publiee, null);
    }

    /** Idem, avec la galerie de photos de l'offre (remplacee si fournie). */
    public MarketplaceDto.ArticleEnLigne publierArticle(Long articleId, boolean publiee, List<String> photos) {
        Offre offre = repository.findAll().stream()
                .filter(o -> o.getType() == Offre.Type.ARTICLE && articleId.equals(o.getArticleId()))
                .findFirst()
                .orElse(null);
        if (offre == null) {
            if (!publiee) {
                return new MarketplaceDto.ArticleEnLigne(articleId, null, false);
            }
            publierArticles(List.of(articleId));
            offre = repository.findAll().stream()
                    .filter(o -> articleId.equals(o.getArticleId())).findFirst()
                    .orElseThrow(() -> new BusinessException("Cet article ne peut pas etre publie (inactif ?)"));
        } else {
            offre.setPubliee(publiee);
            repository.save(offre);
        }
        remplacerPhotos(offre, photos);
        return new MarketplaceDto.ArticleEnLigne(articleId, offre.getId(), offre.isPubliee());
    }

    /** Vehicules du parc proposes (ou retires) a la location en ligne. */
    @Transactional(readOnly = true)
    public List<MarketplaceDto.VehiculeEnLigne> vehiculesEnLigne() {
        return repository.findAll().stream()
                .filter(o -> o.getType() == Offre.Type.VEHICULE && o.getCamionId() != null)
                .map(o -> new MarketplaceDto.VehiculeEnLigne(o.getCamionId(), o.getId(), o.isPubliee(), o.getPrix(),
                        o.getCaution()))
                .toList();
    }

    /**
     * Location en ligne reglee depuis la fiche vehicule : l'offre est creee au
     * premier passage en ligne (titre = marque et modele), puis mise a jour.
     */
    public MarketplaceDto.VehiculeEnLigne publierVehicule(Long camionId, MarketplaceDto.PublicationVehicule r) {
        Offre offre = repository.findAll().stream()
                .filter(o -> o.getType() == Offre.Type.VEHICULE && camionId.equals(o.getCamionId()))
                .findFirst().orElse(null);
        if (offre == null && !r.publiee()) {
            return new MarketplaceDto.VehiculeEnLigne(camionId, null, false, r.tarifJour(), r.caution());
        }
        if (r.publiee() && r.tarifJour() == null) {
            throw new BusinessException("Indiquez le tarif par jour de la location en ligne");
        }
        if (offre == null) {
            Camion camion = camionRepository.findById(camionId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Vehicule", camionId));
            offre = new Offre();
            offre.setType(Offre.Type.VEHICULE);
            offre.setCamionId(camionId);
            offre.setTitre(libelleVehicule(camion));
            offre.setCategorie("Location de vehicules");
            offre.setUnitePrix("jour");
        }
        offre.setPubliee(r.publiee());
        if (r.tarifJour() != null) {
            offre.setPrix(r.tarifJour());
        }
        offre.setCaution(r.caution());
        repository.save(offre);
        remplacerPhotos(offre, r.photos());
        return new MarketplaceDto.VehiculeEnLigne(camionId, offre.getId(), offre.isPubliee(), offre.getPrix(),
                offre.getCaution());
    }

    /** Photos supplementaires de l'offre d'un article (vide sans offre). */
    @Transactional(readOnly = true)
    public List<String> photosArticle(Long articleId) {
        return repository.findAll().stream()
                .filter(o -> o.getType() == Offre.Type.ARTICLE && articleId.equals(o.getArticleId()))
                .findFirst().map(this::photos).orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<String> photosVehicule(Long camionId) {
        return repository.findAll().stream()
                .filter(o -> o.getType() == Offre.Type.VEHICULE && camionId.equals(o.getCamionId()))
                .findFirst().map(this::photos).orElse(List.of());
    }

    /** Galerie publique d'une offre en ligne : photo principale, puis les photos supplementaires. */
    @Transactional(readOnly = true)
    public MarketplaceDto.Galerie galerie(Long offreId) {
        Offre offre = repository.findById(offreId).filter(Offre::isPubliee)
                .orElseThrow(() -> new ResourceNotFoundException("Ce produit n'est plus en vente"));
        List<String> photos = new java.util.ArrayList<>();
        String principale = imageAffichee(offre, sourcesDe(List.of(offre)));
        if (principale != null) {
            photos.add(principale);
        }
        photos.addAll(photos(offre));
        return new MarketplaceDto.Galerie(photos);
    }

    private List<String> photos(Offre offre) {
        return photoRepository.findByOffreIdOrderByOrdreAscIdAsc(offre.getId()).stream()
                .map(OffrePhoto::getImage).toList();
    }

    /** Remplace la galerie de l'offre ; nulle = inchangee. */
    private void remplacerPhotos(Offre offre, List<String> photos) {
        if (photos == null) {
            return;
        }
        if (photos.size() > MarketplaceDto.PHOTOS_MAX) {
            throw new BusinessException("Au plus " + MarketplaceDto.PHOTOS_MAX + " photos supplementaires par produit");
        }
        photoRepository.supprimerDeLOffre(offre.getId());
        for (int i = 0; i < photos.size(); i++) {
            OffrePhoto photo = new OffrePhoto();
            photo.setOffreId(offre.getId());
            photo.setImage(photos.get(i));
            photo.setOrdre(i);
            photoRepository.save(photo);
        }
    }

    public void supprimer(Long id) {
        repository.delete(charger(id));
    }

    @Transactional(readOnly = true)
    public Offre charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Offre", id));
    }

    // ------------------------------------------------------------- Vue publique

    @Transactional(readOnly = true)
    public List<MarketplaceDto.OffrePublique> publiques() {
        List<Offre> offres = repository.findByPublieeTrueOrderByMiseEnAvantDescOrdreAscTitreAsc();
        Sources sources = sourcesDe(offres);
        Map<Long, Long> nombresPhotos = photoRepository.nombresParOffre().stream()
                .collect(Collectors.toMap(l -> (Long) l[0], l -> (Long) l[1]));
        // Avec le point de vente, seuls ses articles se commandent : ils passent par la caisse.
        // Avec la gestion de parc, la boutique propose les vehicules (et, avec le point de vente, ses articles).
        boolean pdv = moduleService.estActif(ContexteEntreprise.exiger(), ModuleApplicatif.POINT_DE_VENTE)
                || moduleService.estActif(ContexteEntreprise.exiger(), ModuleApplicatif.GESTION_PARC);
        return offres.stream()
                .filter(o -> sources.existe(o))
                .filter(o -> !pdv || o.getType() != Offre.Type.LIBRE)
                .map(o -> {
                    BigDecimal reste = disponible(o, sources);
                    return new MarketplaceDto.OffrePublique(o.getId(), o.getType(), o.getTitre(), o.getDescription(),
                            o.getCategorie(), prixAffiche(o, sources), o.getUnitePrix(), o.getCaution(),
                            imageAffichee(o, sources), estDisponible(o, sources),
                            // Le stock exact n'est pas publie : au plus 20 commandables affiches.
                            reste == null ? null : Math.min(reste.intValue(), QUANTITE_MAX_AFFICHEE), o.isMiseEnAvant(),
                            nombresPhotos.getOrDefault(o.getId(), 0L).intValue());
                })
                .toList();
    }

    /** Article du point de vente derriere une offre, s'il y en a un. */
    @Transactional(readOnly = true)
    public Article article(Offre offre) {
        return offre.getArticleId() == null ? null : articleRepository.findById(offre.getArticleId()).orElse(null);
    }

    @Transactional(readOnly = true)
    public Camion camion(Offre offre) {
        return offre.getCamionId() == null ? null : camionRepository.findById(offre.getCamionId()).orElse(null);
    }

    /** Prix facture au client pour une offre. */
    @Transactional(readOnly = true)
    public BigDecimal prix(Offre offre) {
        return prixAffiche(offre, sourcesDe(List.of(offre)));
    }

    @Transactional(readOnly = true)
    public boolean disponible(Offre offre) {
        return estDisponible(offre, sourcesDe(List.of(offre)));
    }

    // ---------------------------------------------------------------- Interne

    private void appliquer(Offre offre, MarketplaceDto.OffreRequete r) {
        offre.setType(r.type());
        switch (r.type()) {
            case ARTICLE -> {
                if (r.articleId() == null) {
                    throw new BusinessException("Choisissez l'article du point de vente a publier");
                }
                articleRepository.findById(r.articleId())
                        .orElseThrow(() -> ResourceNotFoundException.of("Article", r.articleId()));
                offre.setArticleId(r.articleId());
                offre.setCamionId(null);
                offre.setStock(null);
                offre.setCaution(null);
            }
            case VEHICULE -> {
                if (r.camionId() == null) {
                    throw new BusinessException("Choisissez le vehicule a proposer a la location");
                }
                if (r.prix() == null) {
                    throw new BusinessException("Indiquez le tarif de location par jour");
                }
                camionRepository.findById(r.camionId())
                        .orElseThrow(() -> ResourceNotFoundException.of("Vehicule", r.camionId()));
                offre.setCamionId(r.camionId());
                offre.setArticleId(null);
                offre.setStock(null);
                offre.setCaution(r.caution());
            }
            case LIBRE -> {
                if (moduleService.estActif(ContexteEntreprise.exiger(), ModuleApplicatif.POINT_DE_VENTE)) {
                    throw new BusinessException("Avec le point de vente, les produits vendus en ligne passent par la "
                            + "caisse : creez-les dans le catalogue du point de vente, puis publiez-les");
                }
                if (moduleService.estActif(ContexteEntreprise.exiger(), ModuleApplicatif.GESTION_PARC)) {
                    throw new BusinessException("Avec la gestion de parc, la boutique propose vos vehicules a la location : "
                            + "reglez-la depuis la fiche de chaque vehicule");
                }
                if (r.prix() == null) {
                    throw new BusinessException("Indiquez le prix du produit");
                }
                offre.setArticleId(null);
                offre.setCamionId(null);
                offre.setStock(r.stock());
                offre.setCaution(null);
            }
        }
        offre.setTitre(r.titre().trim());
        offre.setDescription(vide(r.description()));
        offre.setCategorie(vide(r.categorie()));
        offre.setPrix(r.prix());
        offre.setUnitePrix(r.type() == Offre.Type.VEHICULE ? "jour" : vide(r.unitePrix()));
        offre.setImage(vide(r.image()));
        offre.setPubliee(r.publiee());
        offre.setMiseEnAvant(r.miseEnAvant());
        offre.setOrdre(r.ordre());
    }

    private MarketplaceDto.OffreReponse versReponse(Offre o, Sources s) {
        String source = switch (o.getType()) {
            case ARTICLE -> s.article(o) == null ? "Article retire du point de vente"
                    : "Article " + s.article(o).getCode();
            case VEHICULE -> s.camion(o) == null ? "Vehicule retire du parc"
                    : s.camion(o).getImmatriculation() + " — " + s.camion(o).getStatut().getLibelle();
            case LIBRE -> "Produit de la boutique";
        };
        return new MarketplaceDto.OffreReponse(o.getId(), o.getType(), o.getArticleId(), o.getCamionId(),
                o.getTitre(), o.getDescription(), o.getCategorie(), o.getPrix(), prixAffiche(o, s), o.getUnitePrix(),
                o.getCaution(), o.getImage(), imageAffichee(o, s), o.getStock(), disponible(o, s), o.isPubliee(),
                o.isMiseEnAvant(), o.getOrdre(), source);
    }

    private BigDecimal prixAffiche(Offre o, Sources s) {
        if (o.getPrix() != null) {
            return o.getPrix();
        }
        Article article = s.article(o);
        return article == null ? BigDecimal.ZERO : article.getPrixVente();
    }

    private String imageAffichee(Offre o, Sources s) {
        if (o.getImage() != null) {
            return o.getImage();
        }
        return switch (o.getType()) {
            case ARTICLE -> s.article(o) == null ? null : s.article(o).getImage();
            case VEHICULE -> s.camion(o) == null ? null : s.camion(o).getPhotoUrl();
            case LIBRE -> null;
        };
    }

    /** Quantite encore vendable ; nulle = sans limite (ou sans objet pour un vehicule). */
    private BigDecimal disponible(Offre o, Sources s) {
        return switch (o.getType()) {
            case ARTICLE -> {
                Article a = s.article(o);
                yield a == null ? BigDecimal.ZERO : a.isSuiviStock() ? lieux.disponibleEnLigne(a).max(BigDecimal.ZERO) : null;
            }
            case LIBRE -> o.getStock() == null ? null : BigDecimal.valueOf(Math.max(0, o.getStock()));
            case VEHICULE -> null;
        };
    }

    private boolean estDisponible(Offre o, Sources s) {
        return switch (o.getType()) {
            case ARTICLE -> {
                Article a = s.article(o);
                yield a != null && a.isActif() && (!a.isSuiviStock() || lieux.disponibleEnLigne(a).signum() > 0);
            }
            case VEHICULE -> {
                Camion c = s.camion(o);
                yield c != null && c.isActif() && !HORS_LOCATION.contains(c.getStatut());
            }
            case LIBRE -> o.getStock() == null || o.getStock() > 0;
        };
    }

    /** Charge en une fois les articles et vehicules derriere une liste d'offres. */
    private Sources sourcesDe(List<Offre> offres) {
        Set<Long> articleIds = offres.stream().map(Offre::getArticleId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> camionIds = offres.stream().map(Offre::getCamionId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Article> articles = articleIds.isEmpty() ? Map.of() : articleRepository.findAllById(articleIds)
                .stream().collect(Collectors.toMap(Article::getId, Function.identity()));
        Map<Long, Camion> camions = camionIds.isEmpty() ? Map.of() : camionRepository.findAllById(camionIds)
                .stream().collect(Collectors.toMap(Camion::getId, Function.identity()));
        return new Sources(articles, camions);
    }

    static String libelleVehicule(Camion c) {
        String libelle = String.join(" ", c.getMarque() == null ? "" : c.getMarque(),
                c.getModele() == null ? "" : c.getModele()).trim();
        return libelle.isEmpty() ? c.getImmatriculation() : libelle;
    }

    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    private record Sources(Map<Long, Article> articles, Map<Long, Camion> camions) {

        Article article(Offre o) {
            return o.getArticleId() == null ? null : articles.get(o.getArticleId());
        }

        Camion camion(Offre o) {
            return o.getCamionId() == null ? null : camions.get(o.getCamionId());
        }

        /** Une offre dont l'article ou le vehicule a disparu n'est plus montree. */
        boolean existe(Offre o) {
            return switch (o.getType()) {
                case ARTICLE -> article(o) != null && article(o).isActif();
                case VEHICULE -> camion(o) != null && camion(o).isActif();
                case LIBRE -> true;
            };
        }
    }
}
