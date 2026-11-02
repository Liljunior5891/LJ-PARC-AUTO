package tg.parcauto.api.marketplace;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Place de marche commune de la plateforme, ouverte a tous. Pas de
 * {@code @ModuleRequis} : elle ne concerne aucune entreprise en particulier,
 * et ne liste que celles qui ont la Marketplace et une boutique publiee.
 */
@RestController
@RequestMapping("/api/public/marche")
@RequiredArgsConstructor
@Tag(name = "Marketplace - place de marche")
public class MarchePublicController {

    private final MarcheService service;

    @GetMapping
    @Operation(summary = "Boutiques et offres de la place de marche, filtrees")
    public MarketplaceDto.Marche parcourir(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) String categorie,
                                           @RequestParam(required = false) String ville) {
        return service.parcourir(q, categorie, ville);
    }
}
