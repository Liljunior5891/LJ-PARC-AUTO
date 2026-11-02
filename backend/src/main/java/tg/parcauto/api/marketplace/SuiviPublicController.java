package tg.parcauto.api.marketplace;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;

/**
 * Suivi d'une commande ou d'une demande de location, ouvert par le lien remis
 * au client. Le jeton designe a la fois l'entreprise et la commande.
 */
@RestController
@ModuleRequis(value = ModuleApplicatif.MARKETPLACE, publique = true)
@RequestMapping("/api/public/suivi/{jetonSuivi}")
@RequiredArgsConstructor
@Tag(name = "Marketplace - suivi client")
public class SuiviPublicController {

    private final CommandeEnLigneRepository commandeRepository;
    private final CommandeEnLigneService commandeService;
    private final DemandeLocationService demandeService;

    @GetMapping
    @Operation(summary = "Etat de la commande ou de la demande de location")
    public MarketplaceDto.Suivi suivi(@PathVariable String jetonSuivi) {
        return commandeRepository.findByJeton(jetonSuivi).isPresent()
                ? commandeService.suivi(jetonSuivi)
                : demandeService.suivi(jetonSuivi);
    }

    @PostMapping("/paiement")
    @Operation(summary = "Relance le paiement mobile money d'une commande qui l'attend")
    public MarketplaceDto.Suivi relancerPaiement(@PathVariable String jetonSuivi) {
        return commandeService.relancerPaiement(jetonSuivi);
    }

    @PostMapping("/paiement/simulation")
    @Operation(summary = "Confirme un paiement de demonstration (fournisseur « simulation » seulement)")
    public MarketplaceDto.Suivi confirmerSimulation(@PathVariable String jetonSuivi) {
        return commandeService.confirmerPaiementSimule(jetonSuivi);
    }
}
