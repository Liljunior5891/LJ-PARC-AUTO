package tg.parcauto.api.pointdevente.lieu;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.catalogue.Conditionnement;
import tg.parcauto.api.pointdevente.materiaux.BonLivraison;
import tg.parcauto.api.pointdevente.materiaux.BonLivraisonRepository;
import tg.parcauto.api.pointdevente.materiaux.LivraisonClientService;
import tg.parcauto.api.pointdevente.materiaux.MateriauxDto;
import tg.parcauto.api.pointdevente.stock.StockService;
import tg.parcauto.api.pointdevente.vente.LigneVente;
import tg.parcauto.api.pointdevente.vente.Vente;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service des bons de retrait au magasin ou a l'entrepot. Le magasinier ne voit
 * que des quantites : ni prix, ni montant, seulement l'indication que le client
 * doit encore de l'argent.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RetraitService {

    /** Passe ce delai, un bon non retire est signale au gerant : il bloque du stock. */
    public static final long JOURS_ALERTE = 30;

    private final BonRetraitRepository repository;
    private final RemiseHorsLigneRepository remisesHorsLigne;
    private final BonLivraisonRepository bonsLivraison;
    private final LieuStockService lieux;
    private final StockLieuRepository stocks;
    private final StockService stockService;
    private final LivraisonClientService livraisons;
    private final PlatformTransactionManager transactions;

    public record LigneRetrait(Long ligneId, Long articleId, String libelle, String unite, BigDecimal quantite,
                               BigDecimal servi, BigDecimal reste,
                               /** Stock present dans le lieu, dans l'unite de base du produit. */
                               BigDecimal enStock) {
    }

    public record Bon(Long id, String numero, Long venteId, String venteNumero, Instant dateEmission, String clientNom,
                      Long lieuId, String lieuNom, LieuStock.Type lieuType, BonRetrait.Statut statut,
                      boolean clientDoitEncore, long jours, boolean ancien, List<LigneRetrait> lignes) {
    }

    public record LigneServie(@NotNull Long ligneId, @NotNull @Positive BigDecimal quantite) {
    }

    public record Remise(@NotEmpty List<@Valid LigneServie> lignes, @Size(max = 150) String recuPar,
                         @Size(max = 255) String note) {
    }

    public record ChangementLieu(@NotNull Long lieuId) {
    }

    /** Remise faite sans connexion, avec sa cle unique generee par l'appareil. */
    public record RemiseDifferee(@NotBlank @Size(max = 64) String cle, @NotNull Long bonId, @NotNull Instant faiteLe,
                                 @NotEmpty List<@Valid LigneServie> lignes, @Size(max = 150) String recuPar,
                                 @Size(max = 255) String note) {
    }

    public record ResultatSynchro(String cle, RemiseHorsLigne.Statut statut, String message, Long bonLivraisonId,
                                  String bonLivraisonNumero) {
    }

    public record Conflit(Long id, String bonNumero, String venteNumero, String lieuNom, String auteur, Instant faiteLe,
                          String recuPar, String detail) {
    }

    // -------------------------------------------------------------- consultation

    @Transactional(readOnly = true)
    public Page<Bon> rechercher(String q, Long lieuId, BonRetrait.Statut statut, UtilisateurPrincipal u, Pageable pageable) {
        List<Long> permis = lieux.idsAccessibles(u);
        List<Long> demandes = lieuId == null ? permis : permis.stream().filter(lieuId::equals).toList();
        if (demandes.isEmpty()) {
            return Page.empty(pageable);
        }
        Set<BonRetrait.Statut> statuts = statut == null ? Set.of(BonRetrait.Statut.values()) : Set.of(statut);
        return repository.recherche(demandes, statuts, q == null ? "" : q.trim().toLowerCase(), pageable).map(this::versDto);
    }

    /** Bon lu ou saisi au comptoir. Un bon d'un autre lieu dit ou aller, sans permettre de le servir. */
    @Transactional(readOnly = true)
    public Bon parNumero(String numero, UtilisateurPrincipal u) {
        BonRetrait bon = repository.findByNumeroIgnoreCase(numero.trim())
                .orElseThrow(() -> new BusinessException("Aucun bon de retrait ne porte le numero " + numero.trim()));
        if (!lieux.accede(u, bon.getLieu())) {
            throw new BusinessException("Le bon " + bon.getNumero() + " se retire a " + bon.getLieu().getNom());
        }
        return versDto(bon);
    }

    @Transactional(readOnly = true)
    public List<Bon> deLaVente(Long venteId) {
        return repository.findByVenteIdOrderByIdAsc(venteId).stream().map(this::versDto).toList();
    }

    /** Tout ce qu'un appareil doit garder pour continuer a servir sans connexion. */
    @Transactional(readOnly = true)
    public List<Bon> pourHorsLigne(UtilisateurPrincipal u) {
        List<Long> permis = lieux.idsAccessibles(u);
        return permis.isEmpty() ? List.of() : repository.aServir(permis).stream().map(this::versDto).toList();
    }

    @Transactional(readOnly = true)
    public BonRetrait charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Bon de retrait", id));
    }

    // -------------------------------------------------------------------- remise

    public MateriauxDto.BonReponse remettre(Long id, Remise demande, UtilisateurPrincipal auteur) {
        BonRetrait bon = charger(id);
        lieux.exigerAcces(auteur, bon.getLieu());
        if (bon.getStatut() != BonRetrait.Statut.A_SERVIR) {
            throw new BusinessException("Le bon " + bon.getNumero()
                    + (bon.getStatut() == BonRetrait.Statut.SERVI ? " est deja entierement servi" : " est annule"));
        }
        List<Long> permises = bon.lignes().stream().map(LigneVente::getId).toList();
        List<MateriauxDto.LigneRemise> lignes = new ArrayList<>();
        for (LigneServie l : demande.lignes()) {
            if (!permises.contains(l.ligneId())) {
                throw new BusinessException("Cet article ne figure pas sur le bon " + bon.getNumero());
            }
            lignes.add(new MateriauxDto.LigneRemise(l.ligneId(), l.quantite()));
        }
        return livraisons.remettre(bon.getVente().getId(), new MateriauxDto.RemiseRequete(lignes, demande.recuPar(), demande.note()), auteur);
    }

    /**
     * Le gerant change le lieu de retrait, seulement si le nouveau lieu a la marchandise.
     * Tout ce qui reste sur le bon part d'un coup : ce qui a deja ete servi reste trace.
     */
    public Bon changerLieu(Long id, Long lieuId, UtilisateurPrincipal auteur) {
        BonRetrait bon = charger(id);
        if (bon.getStatut() != BonRetrait.Statut.A_SERVIR) {
            throw new BusinessException("Le bon " + bon.getNumero() + " n'a plus rien a servir");
        }
        LieuStock nouveau = lieux.demande(lieuId);
        if (nouveau == null) {
            throw new BusinessException("Ce lieu n'est pas disponible");
        }
        if (nouveau.getId().equals(bon.getLieu().getId())) {
            throw new BusinessException("Le bon " + bon.getNumero() + " se retire deja a " + nouveau.getNom());
        }
        Vente vente = bon.getVente();
        for (LigneVente ligne : bon.lignes()) {
            if (ligne.resteBaseALivrer().signum() > 0) {
                stockService.deplacerReservation(ligne.getArticle(), bon.getLieu(), nouveau, ligne.resteBaseALivrer());
                ligne.setLieu(nouveau);
            }
        }
        lieux.emettreBons(vente);
        return repository.findByVenteIdOrderByIdAsc(vente.getId()).stream()
                .filter(b -> b.getLieu().getId().equals(nouveau.getId()))
                .findFirst().map(this::versDto).orElseThrow();
    }

    // ---------------------------------------------------------------- hors ligne

    /**
     * Enregistre les remises faites sans connexion. Chacune a sa propre transaction :
     * une remise impossible ne bloque pas les autres. Elle devient un conflit, car la
     * marchandise est deja partie : refuser l'envoi ferait disparaitre ce fait.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<ResultatSynchro> synchroniser(List<RemiseDifferee> remises, UtilisateurPrincipal auteur) {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        List<ResultatSynchro> resultats = new ArrayList<>();
        for (RemiseDifferee remise : remises) {
            ResultatSynchro resultat = transaction.execute(s -> dejaRecue(remise.cle()));
            if (resultat == null) {
                try {
                    resultat = transaction.execute(s -> enregistrer(remise, auteur));
                } catch (RuntimeException e) {
                    String cause = e instanceof BusinessException ? e.getMessage()
                            : "Le stock a change pendant l'envoi, verifiez ce bon";
                    resultat = transaction.execute(s -> conflit(remise, auteur, cause));
                }
            }
            resultats.add(resultat);
        }
        return resultats;
    }

    private ResultatSynchro dejaRecue(String cle) {
        return remisesHorsLigne.findByCle(cle).map(r -> new ResultatSynchro(cle, r.getStatut(),
                r.getStatut() == RemiseHorsLigne.Statut.ENREGISTREE ? "Deja enregistree" : r.getDetail(),
                r.getBonLivraison() == null ? null : r.getBonLivraison().getId(),
                r.getBonLivraison() == null ? null : r.getBonLivraison().getNumero())).orElse(null);
    }

    private ResultatSynchro enregistrer(RemiseDifferee r, UtilisateurPrincipal auteur) {
        String note = "Saisi sans connexion" + (r.note() == null || r.note().isBlank() ? "" : " - " + r.note().trim());
        MateriauxDto.BonReponse bon = remettre(r.bonId(), new Remise(r.lignes(), r.recuPar(), note), auteur);
        BonLivraison livraison = bonsLivraison.findById(bon.id()).orElseThrow();
        livraison.setDateBon(r.faiteLe());
        RemiseHorsLigne trace = trace(r, auteur, RemiseHorsLigne.Statut.ENREGISTREE, null);
        trace.setBonLivraison(livraison);
        remisesHorsLigne.save(trace);
        return new ResultatSynchro(r.cle(), trace.getStatut(), "Enregistree", bon.id(), bon.numero());
    }

    private ResultatSynchro conflit(RemiseDifferee r, UtilisateurPrincipal auteur, String cause) {
        BonRetrait bon = charger(r.bonId());
        StringBuilder detail = new StringBuilder(cause).append(". Remis sans connexion :");
        for (LigneServie l : r.lignes()) {
            String libelle = bon.getVente().getLignes().stream().filter(x -> x.getId().equals(l.ligneId())).findFirst()
                    .map(LigneVente::getLibelle).orElse("article inconnu");
            detail.append(' ').append(l.quantite().stripTrailingZeros().toPlainString()).append(" x ").append(libelle).append(';');
        }
        String texte = detail.length() > 1000 ? detail.substring(0, 1000) : detail.toString();
        RemiseHorsLigne trace = remisesHorsLigne.save(trace(r, auteur, RemiseHorsLigne.Statut.CONFLIT, texte));
        return new ResultatSynchro(r.cle(), trace.getStatut(), texte, null, null);
    }

    private RemiseHorsLigne trace(RemiseDifferee r, UtilisateurPrincipal auteur, RemiseHorsLigne.Statut statut, String detail) {
        RemiseHorsLigne trace = new RemiseHorsLigne();
        trace.setCle(r.cle());
        trace.setBonRetrait(charger(r.bonId()));
        trace.setStatut(statut);
        trace.setDetail(detail);
        trace.setAuteur(auteur.getNomComplet());
        trace.setFaiteLe(r.faiteLe());
        trace.setRecuPar(r.recuPar());
        return trace;
    }

    @Transactional(readOnly = true)
    public List<Conflit> conflits() {
        return remisesHorsLigne.findByStatutOrderByFaiteLeDesc(RemiseHorsLigne.Statut.CONFLIT).stream()
                .map(r -> new Conflit(r.getId(), r.getBonRetrait().getNumero(), r.getBonRetrait().getVente().getNumero(),
                        r.getBonRetrait().getLieu().getNom(), r.getAuteur(), r.getFaiteLe(), r.getRecuPar(), r.getDetail()))
                .toList();
    }

    /** Le gerant a regle le conflit (inventaire, annulation, appel au client) : il sort de la liste. */
    public void traiterConflit(Long id, UtilisateurPrincipal auteur) {
        RemiseHorsLigne remise = remisesHorsLigne.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Remise", id));
        remise.setStatut(RemiseHorsLigne.Statut.TRAITEE);
        remise.setTraiteePar(auteur.getNomComplet());
        remise.setTraiteeLe(Instant.now());
    }

    // ------------------------------------------------------------------- interne

    private Bon versDto(BonRetrait b) {
        Vente v = b.getVente();
        long jours = ChronoUnit.DAYS.between(b.getDateEmission(), Instant.now());
        List<LigneRetrait> lignes = b.lignes().stream().map(l -> {
            BigDecimal enStock = stocks.findByLieuIdAndArticleId(b.getLieu().getId(), l.getArticle().getId())
                    .map(StockLieu::getStock).orElse(BigDecimal.ZERO);
            return new LigneRetrait(l.getId(), l.getArticle().getId(), l.getLibelle(), unite(l), l.getQuantite(),
                    l.getQuantiteLivree(), l.resteALivrer(), enStock);
        }).toList();
        return new Bon(b.getId(), b.getNumero(), v.getId(), v.getNumero(), b.getDateEmission(), v.getClientNom(),
                b.getLieu().getId(), b.getLieu().getNom(), b.getLieu().getType(), b.getStatut(),
                v.getResteDu() != null && v.getResteDu().signum() > 0, jours,
                b.getStatut() == BonRetrait.Statut.A_SERVIR && jours >= JOURS_ALERTE, lignes);
    }

    private static String unite(LigneVente l) {
        Conditionnement c = l.getConditionnement();
        return c != null ? c.getNom() : l.getArticle().getUnite();
    }
}
