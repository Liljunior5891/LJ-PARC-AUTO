package tg.parcauto.api.marketplace;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.pointdevente.restauration.qr.CodeQr;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Espace de gestion de la Marketplace : boutique, offres, commandes et
 * demandes de location. Reserve aux profils du module (administrateur et
 * gerant), controles par {@link ModuleRequis}.
 */
@RestController
@ModuleRequis(ModuleApplicatif.MARKETPLACE)
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
@Tag(name = "Marketplace - gestion")
public class MarketplaceController {

    /** Adresse de l'application d'ou est demande le code QR (meme regle que les codes QR des tables). */
    private static final Pattern ORIGINE = Pattern.compile("^https?://[A-Za-z0-9.:\\-\\[\\]]+(/[A-Za-z0-9._~\\-/]*)?$");

    private final BoutiqueService boutiqueService;
    private final OffreService offreService;
    private final CommandeEnLigneService commandeService;
    private final DemandeLocationService demandeService;
    private final OffreRepository offreRepository;

    @GetMapping("/resume")
    @Operation(summary = "Ce qui attend une reponse : commandes et demandes nouvelles")
    public MarketplaceDto.Resume resume() {
        MarketplaceDto.BoutiqueReponse boutique = boutiqueService.lire();
        return new MarketplaceDto.Resume(commandeService.compter(CommandeEnLigne.Statut.NOUVELLE),
                commandeService.enCours(), demandeService.nouvelles(),
                offreRepository.findByPublieeTrueOrderByMiseEnAvantDescOrdreAscTitreAsc().size(),
                boutique.id() != null && boutique.publiee(), boutique.slug());
    }

    // ------------------------------------------------------------------ Boutique

    @GetMapping("/boutique")
    @Operation(summary = "Reglages de la boutique (une proposition tant qu'elle n'est pas enregistree)")
    public MarketplaceDto.BoutiqueReponse boutique() {
        return boutiqueService.lire();
    }

    @GetMapping(value = "/boutique/qr", produces = "image/svg+xml")
    @Operation(summary = "Code QR (SVG) de la boutique publique, servie a l'adresse donnee")
    public String codeQr(@RequestParam String origine) {
        if (origine == null || origine.length() > 200 || !ORIGINE.matcher(origine).matches()) {
            throw new BusinessException("Adresse de l'application invalide");
        }
        return CodeQr.svg(origine.replaceAll("/+$", "") + "/boutique/" + boutiqueService.lire().slug());
    }

    @PutMapping("/boutique")
    @Operation(summary = "Enregistre les reglages de la boutique")
    public MarketplaceDto.BoutiqueReponse enregistrerBoutique(@Valid @RequestBody MarketplaceDto.BoutiqueRequete r) {
        return boutiqueService.enregistrer(r);
    }

    // -------------------------------------------------------------------- Offres

    @GetMapping("/offres")
    @Operation(summary = "Offres de la boutique")
    public List<MarketplaceDto.OffreReponse> offres() {
        return offreService.lister();
    }

    @GetMapping("/sources")
    @Operation(summary = "Articles du point de vente et vehicules du parc publiables")
    public MarketplaceDto.Sources sources() {
        return offreService.sources();
    }

    @PostMapping("/offres")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publie une offre")
    public MarketplaceDto.OffreReponse creerOffre(@Valid @RequestBody MarketplaceDto.OffreRequete r) {
        return offreService.creer(r);
    }

    @PostMapping("/offres/articles")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publie plusieurs articles du point de vente")
    public List<MarketplaceDto.OffreReponse> publierArticles(@Valid @RequestBody MarketplaceDto.PublicationArticles r) {
        return offreService.publierArticles(r.articleIds());
    }

    @GetMapping("/articles-en-ligne")
    @Operation(summary = "Articles du point de vente publies dans la boutique en ligne")
    public List<MarketplaceDto.ArticleEnLigne> articlesEnLigne() {
        return offreService.articlesEnLigne();
    }

    @GetMapping("/articles/{articleId}/photos")
    @Operation(summary = "Photos supplementaires de l'article vendu en ligne")
    public MarketplaceDto.Galerie photosArticle(@PathVariable Long articleId) {
        return new MarketplaceDto.Galerie(offreService.photosArticle(articleId));
    }

    @GetMapping("/vehicules/{camionId}/photos")
    @Operation(summary = "Photos supplementaires du vehicule loue en ligne")
    public MarketplaceDto.Galerie photosVehicule(@PathVariable Long camionId) {
        return new MarketplaceDto.Galerie(offreService.photosVehicule(camionId));
    }

    @PutMapping("/articles/{articleId}/en-ligne")
    @Operation(summary = "Met un article du catalogue en vente en ligne, ou l'en retire")
    public MarketplaceDto.ArticleEnLigne publierArticle(@PathVariable Long articleId,
                                                        @Valid @RequestBody MarketplaceDto.Publication r) {
        return offreService.publierArticle(articleId, r.publiee(), r.photos());
    }

    @GetMapping("/vehicules-en-ligne")
    @Operation(summary = "Vehicules du parc proposes a la location en ligne")
    public List<MarketplaceDto.VehiculeEnLigne> vehiculesEnLigne() {
        return offreService.vehiculesEnLigne();
    }

    @PutMapping("/vehicules/{camionId}/en-ligne")
    @Operation(summary = "Propose un vehicule a la location en ligne (tarif par jour, caution), ou l'en retire")
    public MarketplaceDto.VehiculeEnLigne publierVehicule(@PathVariable Long camionId,
                                                          @Valid @RequestBody MarketplaceDto.PublicationVehicule r) {
        return offreService.publierVehicule(camionId, r);
    }

    @PutMapping("/offres/{id}")
    @Operation(summary = "Modifie une offre")
    public MarketplaceDto.OffreReponse modifierOffre(@PathVariable Long id,
                                                     @Valid @RequestBody MarketplaceDto.OffreRequete r) {
        return offreService.modifier(id, r);
    }

    @DeleteMapping("/offres/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Retire une offre de la boutique")
    public void supprimerOffre(@PathVariable Long id) {
        offreService.supprimer(id);
    }

    // ----------------------------------------------------------------- Commandes

    @GetMapping("/commandes")
    @Operation(summary = "Commandes en ligne (filtre : en-cours, terminees ou toutes)")
    public Page<MarketplaceDto.CommandeReponse> commandes(@RequestParam(required = false) String filtre,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return commandeService.lister(filtre, PageRequest.of(page, Math.min(size, 100)));
    }

    @GetMapping("/commandes/{id}")
    @Operation(summary = "Detail d'une commande en ligne")
    public MarketplaceDto.CommandeReponse commande(@PathVariable Long id) {
        return commandeService.detail(id);
    }

    @PostMapping("/commandes/{id}/statut")
    @Operation(summary = "Fait avancer la commande (confirmee, prete, en livraison, livree) ou l'annule")
    public MarketplaceDto.CommandeReponse changerStatut(@PathVariable Long id,
                                                        @Valid @RequestBody MarketplaceDto.ChangementStatut r) {
        return commandeService.changerStatut(id, r);
    }

    @PostMapping("/commandes/{id}/paiement")
    @Operation(summary = "Marque la commande comme payee")
    public MarketplaceDto.CommandeReponse marquerPayee(@PathVariable Long id) {
        return commandeService.marquerPayee(id);
    }

    // ------------------------------------------------------- Demandes de location

    @GetMapping("/demandes-location")
    @Operation(summary = "Demandes de location de vehicules")
    public Page<MarketplaceDto.DemandeLocationReponse> demandes(
            @RequestParam(required = false) DemandeLocation.Statut statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return demandeService.lister(statut, PageRequest.of(page, Math.min(size, 100)));
    }

    @PostMapping("/demandes-location/{id}/accepter")
    @Operation(summary = "Accepte la demande : le contrat de location est cree")
    public MarketplaceDto.DemandeLocationReponse accepter(@PathVariable Long id,
                                                          @Valid @RequestBody MarketplaceDto.Acceptation r) {
        return demandeService.accepter(id, r);
    }

    @GetMapping("/demandes-location/{id}")
    @Operation(summary = "Detail d'une demande de location (pour preremplir la recette en agence)")
    public MarketplaceDto.DemandeLocationReponse demande(@PathVariable Long id) {
        return demandeService.obtenir(id);
    }

    @PostMapping("/demandes-location/{id}/annuler")
    @Operation(summary = "Annule une demande acceptee (le client se desiste) : le vehicule est libere")
    public MarketplaceDto.DemandeLocationReponse annulerDemande(@PathVariable Long id,
                                                                @Valid @RequestBody MarketplaceDto.Refus r) {
        return demandeService.annuler(id, r);
    }

    @PostMapping("/demandes-location/{id}/refuser")
    @Operation(summary = "Refuse la demande")
    public MarketplaceDto.DemandeLocationReponse refuser(@PathVariable Long id,
                                                         @Valid @RequestBody MarketplaceDto.Refus r) {
        return demandeService.refuser(id, r);
    }
}
