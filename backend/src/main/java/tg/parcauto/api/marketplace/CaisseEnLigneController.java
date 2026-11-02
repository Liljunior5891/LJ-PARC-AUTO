package tg.parcauto.api.marketplace;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.Autorisations;

import java.util.EnumSet;
import java.util.List;

/**
 * Commandes de la boutique en ligne vues depuis la caisse boutique : le
 * personnel de caisse les accepte, les prepare, puis les encaisse a la remise
 * (le ticket porte l'identifiant de la commande en ligne, qui se clot alors).
 * Rattache au point de vente : un caissier n'a pas le module Marketplace.
 */
@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@RequestMapping("/api/pdv/commandes-en-ligne")
@RequiredArgsConstructor
@Tag(name = "Point de vente - commandes en ligne")
public class CaisseEnLigneController {

    /** Etapes que la caisse fait elle-meme ; la remise passe par l'encaissement. */
    private static final EnumSet<CommandeEnLigne.Statut> ETAPES_CAISSE = EnumSet.of(
            CommandeEnLigne.Statut.CONFIRMEE, CommandeEnLigne.Statut.PRETE,
            CommandeEnLigne.Statut.EN_LIVRAISON, CommandeEnLigne.Statut.ANNULEE);

    private final CommandeEnLigneService service;

    @GetMapping
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Commandes en ligne a accepter, a preparer ou a encaisser")
    public List<MarketplaceDto.CommandeReponse> aTraiter() {
        return service.aTraiterEnCaisse();
    }

    @PostMapping("/{id}/statut")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Accepte, marque prete, envoie en livraison ou refuse une commande en ligne")
    public MarketplaceDto.CommandeReponse changerStatut(@PathVariable Long id,
                                                        @Valid @RequestBody MarketplaceDto.ChangementStatut r) {
        if (!ETAPES_CAISSE.contains(r.statut())) {
            throw new BusinessException("Etape non permise depuis la caisse");
        }
        return service.changerStatut(id, r);
    }
}
