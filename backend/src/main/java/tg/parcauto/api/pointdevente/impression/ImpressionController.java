package tg.parcauto.api.pointdevente.impression;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.Autorisations;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.util.List;

@RestController
@ModuleRequis(value = ModuleApplicatif.POINT_DE_VENTE, variante = "RESTAURATION")
@RequestMapping("/api/pdv/impression")
@RequiredArgsConstructor
@Tag(name = "Point de vente - impression des bons de preparation")
public class ImpressionController {

    private final ImpressionService service;

    @GetMapping("/imprimantes")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Imprimantes de preparation")
    public List<ImpressionService.ImprimanteReponse> imprimantes() {
        return service.lister();
    }

    @PostMapping("/imprimantes")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Ajoute une imprimante de preparation")
    public ImpressionService.ImprimanteReponse creer(@Valid @RequestBody ImpressionService.ImprimanteRequete r) {
        return service.creer(r);
    }

    @PutMapping("/imprimantes/{id}")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Modifie une imprimante de preparation")
    public ImpressionService.ImprimanteReponse modifier(@PathVariable Long id,
                                                        @Valid @RequestBody ImpressionService.ImprimanteRequete r) {
        return service.modifier(id, r);
    }

    @PostMapping("/imprimantes/{id}/essai")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Envoie un bon d'essai a l'imprimante")
    public ImpressionService.TravailReponse tester(@PathVariable Long id,
                                                   @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.tester(id, principal.getNomComplet());
    }

    @GetMapping("/a-imprimer")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Bons a imprimer par le navigateur de cette caisse")
    public List<ImpressionService.TravailReponse> aImprimer(@RequestParam(required = false) Long caisseId) {
        return service.aImprimer(caisseId);
    }

    @PostMapping("/prise")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Prend des bons pour les imprimer : ils passent imprimes")
    public List<ImpressionService.TravailReponse> prendre(@RequestBody List<Long> ids,
                                                          @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.prendre(ids, principal.getNomComplet());
    }

    @PostMapping("/bons/{id}/duplicata")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Reimprime un bon (DUPLICATA)")
    public ImpressionService.TravailReponse reimprimer(@PathVariable Long id) {
        return service.reimprimer(id);
    }

    @GetMapping("/commandes/{id}")
    @PreAuthorize(Autorisations.PDV_SALLE)
    @Operation(summary = "Bons imprimes pour une commande")
    public List<ImpressionService.TravailReponse> deLaCommande(@PathVariable Long id) {
        return service.deLaCommande(id);
    }

    @GetMapping("/ventes/{id}")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Bons imprimes pour un ticket")
    public List<ImpressionService.TravailReponse> deLaVente(@PathVariable Long id) {
        return service.deLaVente(id);
    }
}
