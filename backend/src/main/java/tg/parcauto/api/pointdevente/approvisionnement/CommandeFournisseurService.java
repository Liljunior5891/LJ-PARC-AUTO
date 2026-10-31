package tg.parcauto.api.pointdevente.approvisionnement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;
import tg.parcauto.api.pointdevente.stock.StockService;
import tg.parcauto.api.pointdevente.stock.TypeMouvementStock;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Commandes de reapprovisionnement : saisie, modification tant qu'elles sont
 * en cours, reception (entree en stock des quantites recues) et annulation.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CommandeFournisseurService {

    private final CommandeFournisseurRepository repository;
    private final ArticleRepository articleRepository;
    private final StockService stockService;
    private final NumerotationService numerotation;

    public record LigneRequete(
            @NotNull Long articleId,
            @NotNull @Positive BigDecimal quantite,
            @PositiveOrZero BigDecimal coutUnitaire) {
    }

    public record Requete(
            @NotBlank @Size(max = 150) String fournisseur,
            LocalDate dateCommande,
            LocalDate livraisonPrevue,
            @Size(max = 500) String note,
            @NotEmpty @Size(max = 200) List<@Valid LigneRequete> lignes) {
    }

    public record LigneReception(@NotNull Long ligneId, @NotNull @PositiveOrZero BigDecimal quantiteRecue,
                                 @PositiveOrZero BigDecimal coutUnitaire) {
    }

    /** Quantites effectivement livrees ; une ligne absente est recue en totalite. */
    public record Reception(List<@Valid LigneReception> lignes) {
    }

    public record Ligne(Long id, Long articleId, String articleCode, String articleNom, String unite,
                        BigDecimal quantite, BigDecimal coutUnitaire, BigDecimal total,
                        BigDecimal quantiteRecue, BigDecimal stockActuel) {

        static Ligne de(LigneCommandeFournisseur l) {
            Article a = l.getArticle();
            return new Ligne(l.getId(), a.getId(), a.getCode(), a.getNom(), a.getUnite(), l.getQuantite(),
                    l.getCoutUnitaire(), montant(l.total()), l.getQuantiteRecue(), a.getStockActuel());
        }
    }

    public record Resume(Long id, String numero, String fournisseur, LocalDate dateCommande,
                         LocalDate livraisonPrevue, CommandeFournisseur.Statut statut, int produits,
                         BigDecimal total, Instant recueLe) {

        static Resume de(CommandeFournisseur c) {
            return new Resume(c.getId(), c.getNumero(), c.getFournisseur(), c.getDateCommande(),
                    c.getLivraisonPrevue(), c.getStatut(), c.getLignes().size(), montant(c.total()), c.getRecueLe());
        }
    }

    public record Detail(Resume resume, String note, String creePar, String recuePar, Instant annuleeLe,
                         List<Ligne> lignes) {

        static Detail de(CommandeFournisseur c) {
            return new Detail(Resume.de(c), c.getNote(), c.getCreePar(), c.getRecuePar(), c.getAnnuleeLe(),
                    c.getLignes().stream().map(Ligne::de).toList());
        }
    }

    @Transactional(readOnly = true)
    public Page<Resume> lister(CommandeFournisseur.Statut statut, Pageable pageable) {
        Page<CommandeFournisseur> page = statut == null
                ? repository.findAllByOrderByDateCommandeDescIdDesc(pageable)
                : repository.findByStatutOrderByDateCommandeDescIdDesc(statut, pageable);
        return page.map(Resume::de);
    }

    @Transactional(readOnly = true)
    public Detail detail(Long id) {
        return Detail.de(charger(id));
    }

    @Transactional(readOnly = true)
    public List<String> fournisseurs() {
        return repository.fournisseurs();
    }

    public Detail creer(Requete r) {
        CommandeFournisseur commande = new CommandeFournisseur();
        commande.setNumero(numerotation.prochainNumero("BC"));
        appliquer(commande, r);
        return Detail.de(repository.saveAndFlush(commande));
    }

    public Detail modifier(Long id, Requete r) {
        CommandeFournisseur commande = chargerEnCours(id);
        appliquer(commande, r);
        repository.flush();
        return Detail.de(commande);
    }

    /**
     * Reception de la marchandise : chaque quantite recue entre en stock au
     * cout de la commande, qui devient le cout d'achat de reference du produit.
     */
    public Detail receptionner(Long id, Reception reception, UtilisateurPrincipal auteur) {
        CommandeFournisseur commande = chargerEnCours(id);
        Map<Long, LigneReception> saisies = reception == null || reception.lignes() == null ? Map.of()
                : reception.lignes().stream().collect(Collectors.toMap(LigneReception::ligneId, Function.identity(),
                        (a, b) -> b));
        boolean recu = false;
        for (LigneCommandeFournisseur ligne : commande.getLignes()) {
            LigneReception saisie = saisies.get(ligne.getId());
            BigDecimal quantite = saisie == null ? ligne.getQuantite() : saisie.quantiteRecue();
            if (saisie != null && saisie.coutUnitaire() != null) {
                ligne.setCoutUnitaire(saisie.coutUnitaire());
            }
            ligne.setQuantiteRecue(quantite);
            if (quantite.signum() > 0) {
                recu = true;
                Article article = ligne.getArticle();
                if (article.isSuiviStock()) {
                    stockService.appliquer(article, TypeMouvementStock.ENTREE, quantite, ligne.getCoutUnitaire(),
                            "Reception " + commande.getNumero() + " - " + commande.getFournisseur(), null);
                }
                if (ligne.getCoutUnitaire() != null) {
                    article.setPrixAchat(ligne.getCoutUnitaire());
                }
            }
        }
        if (!recu) {
            throw new BusinessException("Aucune quantite recue : annulez plutot la commande");
        }
        commande.setStatut(CommandeFournisseur.Statut.RECUE);
        commande.setRecueLe(Instant.now());
        commande.setRecuePar(auteur.getNomComplet());
        return Detail.de(commande);
    }

    public Detail annuler(Long id) {
        CommandeFournisseur commande = chargerEnCours(id);
        commande.setStatut(CommandeFournisseur.Statut.ANNULEE);
        commande.setAnnuleeLe(Instant.now());
        return Detail.de(commande);
    }

    private void appliquer(CommandeFournisseur c, Requete r) {
        c.setFournisseur(r.fournisseur().trim());
        c.setDateCommande(r.dateCommande() == null ? LocalDate.now() : r.dateCommande());
        c.setLivraisonPrevue(r.livraisonPrevue());
        c.setNote(r.note() == null || r.note().isBlank() ? null : r.note().trim());
        Set<Long> vus = new HashSet<>();
        c.getLignes().clear();
        for (LigneRequete lr : r.lignes()) {
            if (!vus.add(lr.articleId())) {
                throw new BusinessException("Un produit figure deux fois sur la commande : regroupez les quantites");
            }
            Article article = articleRepository.findById(lr.articleId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Article", lr.articleId()));
            LigneCommandeFournisseur ligne = new LigneCommandeFournisseur();
            ligne.setArticle(article);
            ligne.setQuantite(lr.quantite());
            ligne.setCoutUnitaire(lr.coutUnitaire() != null ? lr.coutUnitaire() : article.getPrixAchat());
            c.ajouter(ligne);
        }
    }

    private CommandeFournisseur charger(Long id) {
        return repository.detail(id).orElseThrow(() -> ResourceNotFoundException.of("Commande fournisseur", id));
    }

    private CommandeFournisseur chargerEnCours(Long id) {
        CommandeFournisseur commande = charger(id);
        if (commande.getStatut() != CommandeFournisseur.Statut.EN_COURS) {
            throw new BusinessException("La commande " + commande.getNumero() + " est "
                    + (commande.getStatut() == CommandeFournisseur.Statut.RECUE ? "deja receptionnee" : "annulee"));
        }
        return commande;
    }

    private static BigDecimal montant(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
