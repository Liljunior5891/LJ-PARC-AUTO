package tg.parcauto.api.pointdevente.lieu;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.caisse.Caisse;
import tg.parcauto.api.pointdevente.caisse.CaisseRepository;
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;
import tg.parcauto.api.pointdevente.personnel.PersonnelService;
import tg.parcauto.api.pointdevente.vente.LigneVente;
import tg.parcauto.api.pointdevente.vente.Vente;
import tg.parcauto.api.pointdevente.vente.VenteRepository;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Magasins et entrepots. Tant qu'aucun entrepot n'est actif, ce service ne cible
 * aucun lieu et le stock reste gere comme avant, sur l'article seul.
 * <p>
 * Le premier entrepot active le fonctionnement par lieu : un « Magasin principal »
 * recoit tout le stock et toutes les reservations existantes, et les ventes encore
 * a livrer recoivent leurs bons de retrait. L'article garde le total de l'entreprise :
 * rapports et valorisation n'ont pas a connaitre les lieux.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LieuStockService {

    public static final String NOM_PRINCIPAL = "Magasin principal";

    private final LieuStockRepository repository;
    private final StockLieuRepository stockRepository;
    private final ArticleRepository articleRepository;
    private final VenteRepository venteRepository;
    private final BonRetraitRepository bonRepository;
    private final CaisseRepository caisseRepository;
    private final TransfertRepository transfertRepository;
    private final NumerotationService numerotation;
    private final PersonnelService personnel;

    public record Requete(
            @NotBlank @Size(max = 80) String nom,
            @NotNull LieuStock.Type type,
            @Size(max = 255) String adresse,
            @Size(max = 40) String telephone,
            Boolean actif,
            Boolean visibleEnLigne,
            Set<Long> utilisateurIds) {
    }

    public record Reponse(Long id, String nom, LieuStock.Type type, String adresse, String telephone, boolean actif,
                          boolean principal, boolean visibleEnLigne, List<PersonnelService.Membre> utilisateurs,
                          List<String> caisses, long references, BigDecimal quantite, BigDecimal reserve,
                          BigDecimal valeurAchat, BigDecimal valeurVente) {
    }

    /** Ce que l'interface doit savoir : le fonctionnement est-il actif, et ou l'utilisateur travaille. */
    public record Etat(boolean actif, List<LieuCourt> lieux, List<LieuCourt> mesLieux) {
    }

    public record LieuCourt(Long id, String nom, LieuStock.Type type, boolean principal) {
        static LieuCourt de(LieuStock l) {
            return new LieuCourt(l.getId(), l.getNom(), l.getType(), l.isPrincipal());
        }
    }

    public record Disponibilite(Long articleId, Long lieuId, String lieuNom, LieuStock.Type type,
                                BigDecimal stock, BigDecimal reserve, BigDecimal disponible) {
    }

    /** Produit a demander en transfert : ni quantite d'un lieu, ni prix. */
    public record ProduitCourt(Long articleId, String code, String nom, String unite, String image) {
    }

    /** Ligne de stock d'un lieu : des quantites seulement, un magasinier la consulte. */
    public record LigneStock(Long articleId, String code, String nom, String unite, BigDecimal stock, BigDecimal reserve,
                             BigDecimal disponible, BigDecimal seuilAlerte, boolean sousSeuil, String image) {
        static LigneStock de(StockLieu s) {
            Article a = s.getArticle();
            return new LigneStock(a.getId(), a.getCode(), a.getNom(), a.getUnite(), s.getStock(), s.getReserve(), s.disponible(),
                    s.getSeuilAlerte(), s.getSeuilAlerte() != null && s.disponible().compareTo(s.getSeuilAlerte()) <= 0, a.getImage());
        }
    }

    public record Seuil(@PositiveOrZero BigDecimal seuilAlerte) {
    }

    // ------------------------------------------------------------- activation

    /** Vrai des qu'un entrepot actif existe : sinon, la gestion actuelle s'applique. */
    @Transactional(readOnly = true)
    public boolean actif() {
        return repository.existsByTypeAndActifTrue(LieuStock.Type.ENTREPOT);
    }

    /** Lieu sur lequel ecrire : aucun tant que le fonctionnement n'est pas actif, le principal par defaut. */
    @Transactional(readOnly = true)
    public LieuStock cible(LieuStock demande) {
        if (!actif()) {
            return null;
        }
        return demande != null ? demande : principal();
    }

    @Transactional(readOnly = true)
    public LieuStock principal() {
        return repository.findByPrincipalTrue().orElseThrow(() -> new BusinessException("Aucun magasin principal n'est defini"));
    }

    @Transactional(readOnly = true)
    public LieuStock charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Lieu", id));
    }

    /** Lieu actif demande par son identifiant ; nul si rien n'est demande ou si le fonctionnement est eteint. */
    @Transactional(readOnly = true)
    public LieuStock demande(Long id) {
        if (id == null || !actif()) {
            return null;
        }
        LieuStock lieu = charger(id);
        if (!lieu.isActif()) {
            throw new BusinessException("Le lieu " + lieu.getNom() + " est desactive");
        }
        return lieu;
    }

    /** Magasin d'ou vend une caisse : le principal si elle n'en a pas encore. */
    @Transactional(readOnly = true)
    public LieuStock lieuDeCaisse(Caisse caisse) {
        return caisse.getLieu() != null ? caisse.getLieu() : principal();
    }

    @Transactional(readOnly = true)
    public LieuCourt lieuDeCaisseId(Long caisseId) {
        return LieuCourt.de(lieuDeCaisse(caisseRepository.findById(caisseId)
                .orElseThrow(() -> ResourceNotFoundException.of("Caisse", caisseId))));
    }

    /** Ligne de stock du lieu, creee a zero au premier mouvement. */
    public StockLieu ligne(LieuStock lieu, Article article) {
        return stockRepository.findByLieuIdAndArticleId(lieu.getId(), article.getId()).orElseGet(() -> {
            StockLieu s = new StockLieu();
            s.setLieu(lieu);
            s.setArticle(article);
            return stockRepository.save(s);
        });
    }

    // -------------------------------------------------------------- consultation

    @Transactional(readOnly = true)
    public Etat etat(UtilisateurPrincipal u) {
        if (!actif()) {
            return new Etat(false, List.of(), List.of());
        }
        List<LieuStock> actifs = repository.findAllByOrderByPrincipalDescNomAsc().stream().filter(LieuStock::isActif).toList();
        return new Etat(true, actifs.stream().map(LieuCourt::de).toList(),
                actifs.stream().filter(l -> accede(u, l)).map(LieuCourt::de).toList());
    }

    @Transactional(readOnly = true)
    public List<Reponse> lister() {
        List<LieuStock> lieux = repository.findAllByOrderByPrincipalDescNomAsc();
        Map<Long, Object[]> valeurs = new HashMap<>();
        stockRepository.valeurParLieu().forEach(l -> valeurs.put((Long) l[0], l));
        Map<Long, List<String>> caisses = new HashMap<>();
        for (Caisse c : caisseRepository.findAllByOrderByNomAsc()) {
            if (c.isActif() && c.getLieu() != null) {
                caisses.computeIfAbsent(c.getLieu().getId(), k -> new ArrayList<>()).add(c.getNom());
            }
        }
        Map<Long, PersonnelService.Membre> membres = personnel.parId(lieux.stream()
                .flatMap(l -> l.getUtilisateurIds().stream()).collect(java.util.stream.Collectors.toSet()));
        return lieux.stream().map(l -> {
            Object[] v = valeurs.get(l.getId());
            return new Reponse(l.getId(), l.getNom(), l.getType(), l.getAdresse(), l.getTelephone(), l.isActif(), l.isPrincipal(),
                    l.isVisibleEnLigne(), l.getUtilisateurIds().stream().map(membres::get).filter(Objects::nonNull).toList(),
                    caisses.getOrDefault(l.getId(), List.of()),
                    v == null ? 0 : (Long) v[1], v == null ? BigDecimal.ZERO : (BigDecimal) v[2],
                    v == null ? BigDecimal.ZERO : (BigDecimal) v[3], v == null ? BigDecimal.ZERO : (BigDecimal) v[4],
                    v == null ? BigDecimal.ZERO : (BigDecimal) v[5]);
        }).toList();
    }

    /** Disponible de chaque lieu actif, pour que la caisse montre ou trouver un article. */
    @Transactional(readOnly = true)
    public List<Disponibilite> disponibilites(Collection<Long> articles) {
        if (articles == null || articles.isEmpty() || !actif()) {
            return List.of();
        }
        return stockRepository.desArticles(articles).stream()
                .map(s -> new Disponibilite(s.getArticle().getId(), s.getLieu().getId(), s.getLieu().getNom(), s.getLieu().getType(),
                        s.getStock(), s.getReserve(), s.disponible()))
                .toList();
    }

    /**
     * Disponible vu par un utilisateur : la caisse et le gerant voient tous les lieux pour
     * orienter le client ; le magasinier, seulement ceux qui lui sont confies.
     */
    @Transactional(readOnly = true)
    public List<Disponibilite> disponibilites(Collection<Long> articles, UtilisateurPrincipal u) {
        boolean tousLesLieux = PersonnelService.gestionnaire(u)
                || u.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(Role.CAISSIER.authority()));
        if (tousLesLieux) {
            return disponibilites(articles);
        }
        List<Long> permis = idsAccessibles(u);
        return disponibilites(articles).stream().filter(d -> permis.contains(d.lieuId())).toList();
    }

    /** Catalogue pour demander un transfert depuis un lieu dont on ne voit pas le stock. */
    @Transactional(readOnly = true)
    public List<ProduitCourt> produits() {
        return articleRepository.findAll().stream()
                .filter(a -> a.isActif() && a.isSuiviStock())
                .sorted(java.util.Comparator.comparing(Article::getNom, String.CASE_INSENSITIVE_ORDER))
                .map(a -> new ProduitCourt(a.getId(), a.getCode(), a.getNom(), a.getUnite(), a.getImage()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<LigneStock> stockDuLieu(Long lieuId, String q, Pageable pageable, UtilisateurPrincipal u) {
        exigerAcces(u, charger(lieuId));
        return stockRepository.duLieu(lieuId, q == null ? "" : q.trim().toLowerCase(), pageable).map(LigneStock::de);
    }

    public LigneStock modifierSeuil(Long lieuId, Long articleId, BigDecimal seuil) {
        Article article = articleRepository.findById(articleId).orElseThrow(() -> ResourceNotFoundException.of("Article", articleId));
        StockLieu s = ligne(charger(lieuId), article);
        s.setSeuilAlerte(seuil);
        return LigneStock.de(s);
    }

    // ---------------------------------------------------------------- droits

    /** Le gerant et l'administrateur accedent a tous les lieux ; les autres, a ceux qui leur sont confies. */
    @Transactional(readOnly = true)
    public boolean accede(UtilisateurPrincipal u, LieuStock lieu) {
        return PersonnelService.gestionnaire(u) || lieu.getUtilisateurIds().contains(u.getId())
                || caisseRepository.findAllByOrderByNomAsc().stream().anyMatch(c -> c.isActif()
                        && c.getUtilisateurIds().contains(u.getId())
                        && Objects.equals((c.getLieu() != null ? c.getLieu() : principal()).getId(), lieu.getId()));
    }

    @Transactional(readOnly = true)
    public void exigerAcces(UtilisateurPrincipal u, LieuStock lieu) {
        if (!accede(u, lieu)) {
            throw new BusinessException("Le lieu " + lieu.getNom() + " ne vous est pas affecte");
        }
    }

    @Transactional(readOnly = true)
    public List<Long> idsAccessibles(UtilisateurPrincipal u) {
        return repository.findAllByOrderByPrincipalDescNomAsc().stream()
                .filter(LieuStock::isActif).filter(l -> accede(u, l)).map(LieuStock::getId).toList();
    }

    // ------------------------------------------------------------------- CRUD

    public Reponse creer(Requete r) {
        String nom = r.nom().trim();
        if (repository.existsByNomIgnoreCase(nom)) {
            throw new BusinessException("Un lieu porte deja le nom " + nom);
        }
        boolean premierEntrepot = r.type() == LieuStock.Type.ENTREPOT && !actif();
        if (r.type() == LieuStock.Type.MAGASIN && !actif()) {
            throw new BusinessException("Enregistrez d'abord un entrepot : sans entrepot, la boutique garde un seul stock");
        }
        LieuStock lieu = new LieuStock();
        lieu.setType(r.type());
        appliquer(lieu, r);
        repository.saveAndFlush(lieu);
        if (premierEntrepot) {
            activer();
        }
        return lister().stream().filter(l -> l.id().equals(lieu.getId())).findFirst().orElseThrow();
    }

    public Reponse modifier(Long id, Requete r) {
        LieuStock lieu = charger(id);
        String nom = r.nom().trim();
        if (repository.existsByNomIgnoreCaseAndIdNot(nom, id)) {
            throw new BusinessException("Un lieu porte deja le nom " + nom);
        }
        if (r.type() != lieu.getType()) {
            throw new BusinessException("Un magasin ne devient pas un entrepot : creez un nouveau lieu et transferez le stock");
        }
        if (Boolean.FALSE.equals(r.actif()) && lieu.isActif()) {
            verifierFermeture(lieu);
        }
        boolean rouvreEntrepot = Boolean.TRUE.equals(r.actif()) && !lieu.isActif() && lieu.getType() == LieuStock.Type.ENTREPOT && !actif();
        appliquer(lieu, r);
        repository.flush();
        if (rouvreEntrepot) {
            activer();
        }
        return lister().stream().filter(l -> l.id().equals(id)).findFirst().orElseThrow();
    }

    private void verifierFermeture(LieuStock lieu) {
        if (lieu.isPrincipal()) {
            throw new BusinessException("Le magasin principal ne peut pas etre desactive");
        }
        if (stockRepository.lignesNonVides(lieu.getId()) > 0) {
            throw new BusinessException(lieu.getNom() + " contient encore de la marchandise ou des reservations : transferez-les d'abord");
        }
        if (transfertRepository.enCoursDuLieu(lieu.getId()) > 0) {
            throw new BusinessException("Des transferts sont en cours sur " + lieu.getNom());
        }
        if (caisseRepository.findAllByOrderByNomAsc().stream().anyMatch(c -> c.isActif() && c.getLieu() != null
                && c.getLieu().getId().equals(lieu.getId()))) {
            throw new BusinessException("Des caisses sont rattachees a " + lieu.getNom() + " : rattachez-les a un autre magasin");
        }
        // Sans entrepot, tout revient au stock unique : il ne doit plus rester qu'un magasin.
        if (lieu.getType() == LieuStock.Type.ENTREPOT
                && repository.countByActifTrueAndTypeAndIdNot(LieuStock.Type.ENTREPOT, lieu.getId()) == 0
                && repository.countByActifTrueAndType(LieuStock.Type.MAGASIN) > 1) {
            throw new BusinessException("C'est le dernier entrepot : desactivez d'abord les autres magasins");
        }
    }

    private void appliquer(LieuStock l, Requete r) {
        l.setNom(r.nom().trim());
        l.setAdresse(r.adresse() == null || r.adresse().isBlank() ? null : r.adresse().trim());
        l.setTelephone(r.telephone() == null || r.telephone().isBlank() ? null : r.telephone().trim());
        if (r.actif() != null) {
            l.setActif(r.actif());
        }
        if (r.visibleEnLigne() != null) {
            l.setVisibleEnLigne(r.visibleEnLigne());
        }
        if (r.utilisateurIds() != null) {
            l.getUtilisateurIds().clear();
            // Le personnel d'un lieu, ce sont ses magasiniers ; la caisse y accede par son magasin.
            l.getUtilisateurIds().addAll(personnel.verifier(r.utilisateurIds(), java.util.EnumSet.of(Role.MAGASINIER), "Magasinier"));
        }
    }

    /**
     * Passage au fonctionnement par lieu : tout le stock et toutes les reservations
     * vont au magasin principal. Les autres lieux sont vides : un lieu ne se ferme
     * pas tant qu'il garde de la marchandise.
     */
    private void activer() {
        LieuStock principal = repository.findByPrincipalTrue().orElseGet(() -> {
            LieuStock l = new LieuStock();
            l.setNom(NOM_PRINCIPAL);
            l.setType(LieuStock.Type.MAGASIN);
            l.setPrincipal(true);
            l.setVisibleEnLigne(true);
            return repository.saveAndFlush(l);
        });
        for (Article article : articleRepository.findAll()) {
            if (article.isSuiviStock()) {
                StockLieu s = ligne(principal, article);
                s.setStock(article.getStockActuel());
                s.setReserve(article.getStockReserve());
            }
        }
        for (Caisse caisse : caisseRepository.findAllByOrderByNomAsc()) {
            if (caisse.getLieu() == null) {
                caisse.setLieu(principal);
            }
        }
        for (Vente vente : venteRepository.aLivrer("", null, Pageable.unpaged()).getContent()) {
            emettreBons(vente);
        }
        // Un retour au stock unique a pu servir des bons sans les mettre a jour.
        for (BonRetrait bon : bonRepository.findAll()) {
            if (bon.getStatut() == BonRetrait.Statut.A_SERVIR) {
                actualiser(bon);
            }
        }
    }

    // ------------------------------------------------------------ bons de retrait

    /**
     * Un bon par lieu qui a encore quelque chose a remettre. Un bon existant reprend
     * du service plutot que d'en imprimer un nouveau : le client garde son papier.
     */
    public List<BonRetrait> emettreBons(Vente vente) {
        List<BonRetrait> existants = new ArrayList<>(bonRepository.findByVenteIdOrderByIdAsc(vente.getId()));
        Map<Long, LieuStock> lieux = new LinkedHashMap<>();
        for (LigneVente ligne : vente.getLignes()) {
            if (ligne.stockSuivi() && ligne.resteALivrer().signum() > 0) {
                if (ligne.getLieu() == null) {
                    ligne.setLieu(principal());
                }
                lieux.putIfAbsent(ligne.getLieu().getId(), ligne.getLieu());
            }
        }
        for (LieuStock lieu : lieux.values()) {
            if (existants.stream().noneMatch(b -> b.getLieu().getId().equals(lieu.getId()))) {
                BonRetrait bon = new BonRetrait();
                bon.setNumero(numerotation.prochainNumero("BR"));
                bon.setVente(vente);
                bon.setLieu(lieu);
                bon.setDateEmission(Instant.now());
                existants.add(bonRepository.save(bon));
            }
        }
        existants.forEach(this::actualiser);
        return existants;
    }

    /** Remet le statut du bon d'accord avec les lignes de la vente. */
    public void actualiser(BonRetrait bon) {
        if (bon.getVente().getStatut() != Vente.Statut.VALIDEE || bon.lignes().isEmpty()) {
            bon.setStatut(BonRetrait.Statut.ANNULE);
            return;
        }
        bon.setStatut(bon.lignes().stream().anyMatch(l -> l.resteALivrer().signum() > 0)
                ? BonRetrait.Statut.A_SERVIR : BonRetrait.Statut.SERVI);
    }

    /** Apres une remise, un solde ou leur annulation. Sans fonctionnement par lieu, il n'y a rien a suivre. */
    public void actualiserBons(Vente vente) {
        if (actif()) {
            emettreBons(vente);
        }
    }

    // ------------------------------------------------------------ boutique en ligne

    /** Disponible en ligne : les lieux choisis par l'administrateur, ou le stock unique sans entrepot. */
    @Transactional(readOnly = true)
    public BigDecimal disponibleEnLigne(Article article) {
        return repository.multiLieux(article.getEntrepriseId())
                ? stockRepository.disponibleEnLigne(article.getId()) : article.stockDisponible();
    }

    /** Lieu d'ou part une commande en ligne : le premier lieu visible qui a la quantite. */
    @Transactional(readOnly = true)
    public LieuStock lieuEnLigne(Article article, BigDecimal quantite) {
        if (!actif()) {
            return null;
        }
        return stockRepository.enLigne(article.getId()).stream()
                .filter(s -> s.disponible().compareTo(quantite) >= 0)
                .map(StockLieu::getLieu)
                .findFirst()
                .orElseThrow(() -> new BusinessException("Stock insuffisant pour " + article.getNom()));
    }
}
