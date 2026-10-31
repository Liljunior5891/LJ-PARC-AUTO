package tg.parcauto.api.pointdevente.restauration.qr;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.parametrage.SocieteService;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;
import tg.parcauto.api.pointdevente.catalogue.CategorieRepository;
import tg.parcauto.api.pointdevente.restauration.Commande;
import tg.parcauto.api.pointdevente.restauration.CommandeRepository;
import tg.parcauto.api.pointdevente.restauration.CommandeService;
import tg.parcauto.api.pointdevente.restauration.LigneCommande;
import tg.parcauto.api.pointdevente.restauration.ReglageRestauration;
import tg.parcauto.api.pointdevente.restauration.ReglageService;
import tg.parcauto.api.pointdevente.restauration.RestaurationDto;
import tg.parcauto.api.pointdevente.restauration.TableRestaurant;
import tg.parcauto.api.pointdevente.restauration.TableRestaurantRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Carte en ligne d'une table : le client consulte la carte, commande, suit sa
 * commande, appelle un serveur ou demande l'addition. L'entreprise et la table
 * viennent du jeton du code QR (voir {@link ContexteTableInterceptor}).
 * <p>
 * Protection contre les abus : quantites et nombre de lignes bornes, au plus
 * {@value #ENVOIS_MAX} envois par table et par {@value #FENETRE_MINUTES}
 * minutes, et par defaut la validation d'un serveur avant la cuisine.
 */
@Service
@RequiredArgsConstructor
public class CarteEnLigneService {

    static final int ENVOIS_MAX = 6;
    static final int FENETRE_MINUTES = 5;

    private final TableRestaurantRepository tableRepository;
    private final CommandeRepository commandeRepository;
    private final CategorieRepository categorieRepository;
    private final ArticleRepository articleRepository;
    private final CommandeService commandeService;
    private final ReglageService reglageService;
    private final SocieteService societeService;
    private final tg.parcauto.api.pointdevente.caisse.SessionTravailService sessionsTravail;

    /** Horodatage des derniers envois par table, pour freiner les envois en rafale. */
    private final Map<Long, Deque<Instant>> envois = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public CarteEnLigneDto.Carte carte(Long tableId) {
        TableRestaurant table = table(tableId);
        ReglageRestauration reglage = reglageService.courant();
        var identite = societeService.lire();
        List<CarteEnLigneDto.Categorie> categories = categorieRepository.findAllByOrderByOrdreAscNomAsc().stream()
                .filter(c -> c.isActif())
                .map(c -> new CarteEnLigneDto.Categorie(c.getId(), c.getNom(), c.getCouleur(), c.getIcone()))
                .toList();
        List<CarteEnLigneDto.Article> articles = articleRepository.findAll().stream()
                .filter(a -> a.isActif())
                .sorted(Comparator.comparing(a -> a.getNom().toLowerCase()))
                .map(a -> new CarteEnLigneDto.Article(a.getId(), a.getNom(),
                        a.getCategorie() == null ? null : a.getCategorie().getId(), a.getPrixVente(), a.getImage(),
                        !a.isSuiviStock() || a.getStockActuel().signum() > 0))
                .toList();
        // Caisse fermee : la carte se consulte, mais ne prend ni commande ni appel.
        boolean caisseOuverte = sessionsTravail.uneCaisseOuverte();
        return new CarteEnLigneDto.Carte(identite.nom(), identite.logo(), reglage.getMessageAccueil(),
                table.getNom(), reglage.isQrActif() && caisseOuverte, reglage.isQrValidation(),
                categories, articles, suivi(tableId), caisseOuverte);
    }

    @Transactional
    public CarteEnLigneDto.Suivi commander(Long tableId, CarteEnLigneDto.Demande demande) {
        TableRestaurant table = table(tableId);
        ReglageRestauration reglage = reglageService.courant();
        if (!reglage.isQrActif()) {
            throw new BusinessException("La commande en ligne n'est pas ouverte : appelez un serveur");
        }
        exigerCaisseOuverte();
        freiner(tableId);
        commandeService.commandeClient(table, demande.lignes().stream()
                .map(l -> new RestaurationDto.AjoutLigne(l.articleId(), l.quantite(), l.note()))
                .toList(), reglage.isQrValidation());
        return suivi(tableId);
    }

    @Transactional
    public CarteEnLigneDto.Suivi appeler(Long tableId, Commande.Appel motif) {
        exigerCaisseOuverte();
        freiner(tableId);
        commandeService.appelClient(table(tableId), motif);
        return suivi(tableId);
    }

    private void exigerCaisseOuverte() {
        if (!sessionsTravail.uneCaisseOuverte()) {
            throw new BusinessException("L'etablissement ne prend pas de commande pour le moment : la caisse est fermee");
        }
    }

    @Transactional(readOnly = true)
    public CarteEnLigneDto.Suivi suivi(Long tableId) {
        return commandeRepository.ouvertes().stream()
                .filter(c -> c.getTable() != null && c.getTable().getId().equals(tableId))
                .findFirst()
                .map(CarteEnLigneService::versSuivi)
                .orElse(null);
    }

    private static CarteEnLigneDto.Suivi versSuivi(Commande c) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal reste = BigDecimal.ZERO;
        for (LigneCommande l : c.getLignes()) {
            if (l.getStatut().facturable()) {
                total = total.add(l.total());
            }
            if (l.aPayer()) {
                reste = reste.add(l.total());
            }
        }
        List<CarteEnLigneDto.LigneSuivi> lignes = c.getLignes().stream()
                .filter(l -> l.getStatut() != LigneCommande.Statut.ANNULEE)
                .map(l -> new CarteEnLigneDto.LigneSuivi(l.getLibelle(), l.getQuantite(),
                        l.total().setScale(2, RoundingMode.HALF_UP), l.getNote(), l.getStatut()))
                .toList();
        return new CarteEnLigneDto.Suivi(c.getNumero(), lignes, total.setScale(2, RoundingMode.HALF_UP),
                reste.setScale(2, RoundingMode.HALF_UP), c.getAppelMotif(), c.getAppelLe());
    }

    /** Au-dela de quelques envois rapproches, la table doit patienter ou appeler un serveur. */
    private void freiner(Long tableId) {
        Instant maintenant = Instant.now();
        Instant limite = maintenant.minus(Duration.ofMinutes(FENETRE_MINUTES));
        Deque<Instant> historique = envois.computeIfAbsent(tableId, id -> new ArrayDeque<>());
        synchronized (historique) {
            while (!historique.isEmpty() && historique.peekFirst().isBefore(limite)) {
                historique.pollFirst();
            }
            if (historique.size() >= ENVOIS_MAX) {
                throw new BusinessException("Trop de demandes depuis cette table : patientez un instant ou appelez un serveur");
            }
            historique.addLast(maintenant);
        }
    }

    private TableRestaurant table(Long id) {
        return tableRepository.findById(id)
                .filter(TableRestaurant::isActif)
                .orElseThrow(() -> new ResourceNotFoundException("Cette table n'existe plus"));
    }
}
