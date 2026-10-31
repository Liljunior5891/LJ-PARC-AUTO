package tg.parcauto.api.pointdevente.restauration.qr;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;

/**
 * Carte en ligne ouverte par le code QR d'une table, sans compte client. Le
 * jeton de l'adresse designe l'etablissement et la table ; l'etablissement
 * doit avoir un point de vente en mode restaurant.
 */
@RestController
@ModuleRequis(value = ModuleApplicatif.POINT_DE_VENTE, variante = "RESTAURATION", publique = true)
@RequestMapping("/api/public/qr/{jeton}")
@RequiredArgsConstructor
@Tag(name = "Point de vente - carte en ligne (code QR)")
public class CarteEnLigneController {

    private final CarteEnLigneService service;

    @GetMapping
    @Operation(summary = "Carte de l'etablissement et commande en cours de la table")
    public CarteEnLigneDto.Carte carte(@PathVariable String jeton, HttpServletRequest requete) {
        return service.carte(table(requete));
    }

    @GetMapping("/suivi")
    @Operation(summary = "Suivi de la commande en cours de la table")
    public CarteEnLigneDto.Suivi suivi(@PathVariable String jeton, HttpServletRequest requete) {
        return service.suivi(table(requete));
    }

    @PostMapping("/commande")
    @Operation(summary = "Commande du client, validee par un serveur selon le reglage de l'etablissement")
    public CarteEnLigneDto.Suivi commander(@PathVariable String jeton, HttpServletRequest requete,
                                           @Valid @RequestBody CarteEnLigneDto.Demande demande) {
        return service.commander(table(requete), demande);
    }

    @PostMapping("/appel")
    @Operation(summary = "Appelle un serveur ou demande l'addition")
    public CarteEnLigneDto.Suivi appeler(@PathVariable String jeton, HttpServletRequest requete,
                                         @Valid @RequestBody CarteEnLigneDto.Appel appel) {
        return service.appeler(table(requete), appel.motif());
    }

    private static Long table(HttpServletRequest requete) {
        return (Long) requete.getAttribute(ContexteTableInterceptor.TABLE);
    }
}
