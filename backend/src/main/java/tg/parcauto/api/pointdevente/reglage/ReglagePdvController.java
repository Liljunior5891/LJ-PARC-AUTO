package tg.parcauto.api.pointdevente.reglage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.Autorisations;

/** Options du point de vente : lues par tout le personnel, reglees par l'administrateur. */
@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@tg.parcauto.api.plateforme.module.OuvertAuxAssocies
@RequestMapping("/api/pdv/reglages")
@RequiredArgsConstructor
@Tag(name = "Point de vente - reglages")
public class ReglagePdvController {

    private final ReglagePdvService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','GERANT','CAISSIER','SERVEUR','PREPARATEUR','MAGASINIER','ASSOCIE')")
    @Operation(summary = "Options activees du point de vente")
    public ReglagePdvService.Reglages lire() {
        return service.lire();
    }

    @PutMapping
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Active ou desactive les options du point de vente")
    public ReglagePdvService.Reglages modifier(@Valid @RequestBody ReglagePdvService.Requete requete) {
        return service.modifier(requete);
    }
}
