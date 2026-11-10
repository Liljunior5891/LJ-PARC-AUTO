package tg.parcauto.api.marketplace;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;

/**
 * Boutique publique d'une entreprise, ouverte sans compte. L'adresse
 * ({@code slug}) designe l'entreprise (voir {@link ContexteBoutiqueInterceptor}),
 * qui doit avoir le module Marketplace et une boutique publiee.
 */
@RestController
@ModuleRequis(value = ModuleApplicatif.MARKETPLACE, publique = true)
@RequestMapping("/api/public/boutiques/{slug}")
@RequiredArgsConstructor
@Tag(name = "Marketplace - boutique publique")
public class BoutiquePubliqueController {

    private final BoutiqueService boutiqueService;
    private final tg.parcauto.api.common.LimiteurRequetes limiteur;
    private final OffreService offreService;
    private final CommandeEnLigneService commandeService;
    private final DemandeLocationService demandeService;
    private final tg.parcauto.api.plateforme.module.ModuleService moduleService;
    private final tg.parcauto.api.pointdevente.caisse.SessionTravailService sessionsTravail;

    @GetMapping
    @Operation(summary = "Vitrine de la boutique et ses offres")
    public MarketplaceDto.Vitrine vitrine(@PathVariable String slug) {
        Boutique b = boutiqueService.publiee();
        // Avec le point de vente, la boutique ne prend commande que caisse ouverte.
        boolean ouverte = !moduleService.estActif(tg.parcauto.api.plateforme.tenant.ContexteEntreprise.exiger(),
                ModuleApplicatif.POINT_DE_VENTE) || sessionsTravail.uneCaisseOuverte();
        return new MarketplaceDto.Vitrine(b.getSlug(), b.getNom(), b.getSlogan(), b.getDescription(),
                b.getCategorie(), b.getCouverture(), boutiqueService.logo(), b.getTelephone(), b.getWhatsapp(),
                b.getAdresse(), b.getVille(), b.getHoraires(), b.isRetraitActif(), b.isLivraisonActive(),
                b.getFraisLivraison(), b.getZoneLivraison(), b.isPaiementRemise(),
                boutiqueService.mobileMoneyOuvert(b), offreService.publiques(), ouverte,
                ouverte ? null : "Ferme pour le moment : les commandes reprennent a l'ouverture de la caisse.");
    }

    @GetMapping("/offres/{offreId}/photos")
    @Operation(summary = "Galerie d'une offre : photo principale et photos supplementaires")
    public MarketplaceDto.Galerie galerie(@PathVariable String slug, @PathVariable Long offreId) {
        return offreService.galerie(offreId);
    }

    @PostMapping("/commandes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Passe une commande (sans compte : nom et telephone)")
    public MarketplaceDto.Suivi commander(@PathVariable String slug,
                                          @Valid @RequestBody MarketplaceDto.DemandeCommande demande,
                                          jakarta.servlet.http.HttpServletRequest requete) {
        // Point public : quelques commandes par heure et par connexion, contre les commandes en serie.
        limiteur.exiger("commande:" + requete.getRemoteAddr(), 10, java.time.Duration.ofHours(1),
                "Trop de commandes depuis cette connexion : reessayez plus tard ou appelez la boutique");
        return commandeService.commander(demande);
    }

    @PostMapping("/locations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Demande la location d'un vehicule sur une periode")
    public MarketplaceDto.Suivi demanderLocation(@PathVariable String slug,
                                                 @Valid @RequestBody MarketplaceDto.DemandeLocationPublique demande,
                                                 jakarta.servlet.http.HttpServletRequest requete) {
        limiteur.exiger("location:" + requete.getRemoteAddr(), 5, java.time.Duration.ofHours(1),
                "Trop de demandes depuis cette connexion : reessayez plus tard ou appelez la boutique");
        return demandeService.demander(demande);
    }
}
