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
    private final tg.parcauto.api.pointdevente.lieu.LieuStockService lieux;
    private final NumerotationService numerotation;
    private final tg.parcauto.api.pointdevente.fournisseur.FournisseurService fournisseurService;

    public record LigneRequete(
            @NotNull Long articleId,
            @NotNull @Positive BigDecimal quantite,
            @PositiveOrZero BigDecimal coutUnitaire) {
    }

    public record Requete(
            /** Fiche du fournisseur ; a defaut, celle qui porte ce nom, creee au besoin. */
            Long fournisseurId,
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
    public record Reception(List<@Valid LigneReception> lignes,
                            /** Lieu qui recoit la marchandise, quand l'entreprise a des entrepots ; le principal sinon. */
                            Long lieuId) {

        public Reception(List<LigneReception> lignes) {
            this(lignes, null);
        }
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
                         BigDecimal total, Instant recueLe, Long fournisseurId, BigDecimal montantRecu,
                         LocalDate echeance,
                         /** Etat du paiement au fournisseur ; nul tant que la commande n'est pas receptionnee. */
                         EtatPaiement paiement, BigDecimal regle, BigDecimal reste) {

        static Resume de(CommandeFournisseur c) {
            return de(c, null);
        }

        static Resume de(CommandeFournisseur c, tg.parcauto.api.pointdevente.fournisseur.FournisseurService.Dette dette) {
            EtatPaiement etat = null;
            if (c.getStatut() == CommandeFournisseur.Statut.RECUE && dette != null) {
                if (!c.isDetteSuivie() || dette.reste().signum() == 0) {
                    etat = EtatPaiement.PAYEE;
                } else if (dette.enRetard()) {
                    etat = EtatPaiement.EN_RETARD;
                } else {
                    etat = dette.regle().signum() > 0 ? EtatPaiement.PARTIELLE : EtatPaiement.NON_PAYEE;
                }
            }
            return new Resume(c.getId(), c.getNumero(), c.getFournisseur(), c.getDateCommande(),
                    c.getLivraisonPrevue(), c.getStatut(), c.getLignes().size(), montant(c.total()), c.getRecueLe(),
                    c.getFiche() == null ? null : c.getFiche().getId(), c.getMontantRecu(), c.getEcheance(), etat,
                    dette == null ? null : dette.regle(),
                    dette == null ? null : (c.isDetteSuivie() ? dette.reste() : BigDecimal.ZERO));
        }
    }

    /** Ou en est le paiement d'une commande receptionnee. */
    public enum EtatPaiement { PAYEE, PARTIELLE, NON_PAYEE, EN_RETARD }

    public record Detail(Resume resume, String note, String creePar, String recuePar, Instant annuleeLe,
                         List<Ligne> lignes,
                         /** Ce qui a ete paye et ce qu'il reste a payer au fournisseur. */
                         tg.parcauto.api.pointdevente.fournisseur.FournisseurService.Situation paiement) {
    }

    private Detail detailDe(CommandeFournisseur c) {
        return new Detail(Resume.de(c, fournisseurService.dettesDe(List.of(c)).get(c.getId())), c.getNote(), c.getCreePar(), c.getRecuePar(), c.getAnnuleeLe(),
                c.getLignes().stream().map(Ligne::de).toList(), fournisseurService.situation(c));
    }

    @Transactional(readOnly = true)
    public Page<Resume> lister(CommandeFournisseur.Statut statut, Pageable pageable) {
        Page<CommandeFournisseur> page = statut == null
                ? repository.findAllByOrderByDateCommandeDescIdDesc(pageable)
                : repository.findByStatutOrderByDateCommandeDescIdDesc(statut, pageable);
        Map<Long, tg.parcauto.api.pointdevente.fournisseur.FournisseurService.Dette> dettes =
                fournisseurService.dettesDe(page.getContent());
        return page.map(c -> Resume.de(c, dettes.get(c.getId())));
    }

    @Transactional(readOnly = true)
    public Detail detail(Long id) {
        return detailDe(charger(id));
    }

    @Transactional(readOnly = true)
    public List<String> fournisseurs() {
        return fournisseurService.actifs().stream()
                .map(tg.parcauto.api.pointdevente.fournisseur.FournisseurService.Reponse::nom).toList();
    }

    public Detail creer(Requete r) {
        CommandeFournisseur commande = new CommandeFournisseur();
        commande.setNumero(numerotation.prochainNumero("BC"));
        appliquer(commande, r);
        return detailDe(repository.saveAndFlush(commande));
    }

    public Detail modifier(Long id, Requete r) {
        CommandeFournisseur commande = chargerEnCours(id);
        appliquer(commande, r);
        repository.flush();
        return detailDe(commande);
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
        tg.parcauto.api.pointdevente.lieu.LieuStock lieu = lieux.cible(lieux.demande(reception == null ? null : reception.lieuId()));
        commande.setLieu(lieu);
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
                    stockService.appliquer(article, lieu, TypeMouvementStock.ENTREE, quantite, ligne.getCoutUnitaire(),
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
        fournisseurService.ouvrirDette(commande);
        return detailDe(commande);
    }

    public Detail annuler(Long id) {
        CommandeFournisseur commande = chargerEnCours(id);
        commande.setStatut(CommandeFournisseur.Statut.ANNULEE);
        commande.setAnnuleeLe(Instant.now());
        return detailDe(commande);
    }

    private void appliquer(CommandeFournisseur c, Requete r) {
        tg.parcauto.api.pointdevente.fournisseur.Fournisseur fiche =
                fournisseurService.ficheDeCommande(r.fournisseurId(), r.fournisseur());
        c.setFiche(fiche);
        c.setFournisseur(fiche.getNom());
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
