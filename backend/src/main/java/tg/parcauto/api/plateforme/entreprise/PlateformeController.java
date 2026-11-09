package tg.parcauto.api.plateforme.entreprise;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.security.Autorisations;

import java.util.List;

/** Console de l'exploitant de la plateforme : entreprises abonnees. */
@RestController
@RequestMapping("/api/plateforme")
@RequiredArgsConstructor
@PreAuthorize(Autorisations.PLATEFORME)
@Tag(name = "Plateforme")
public class PlateformeController {

    private final PlateformeService service;

    @GetMapping("/statistiques")
    @Operation(summary = "Chiffres cles de la plateforme")
    public PlateformeService.Statistiques statistiques() {
        return service.statistiques();
    }

    @GetMapping("/entreprises")
    @Operation(summary = "Entreprises abonnees, leurs comptes et leurs modules")
    public List<PlateformeService.EntrepriseResume> entreprises() {
        return service.entreprises();
    }

    @PatchMapping("/entreprises/{id}/statut")
    @Operation(summary = "Suspend ou reactive une entreprise ; ses comptes sont bloques tant qu'elle est suspendue")
    public PlateformeService.EntrepriseResume changerStatut(@PathVariable Long id,
                                                           @RequestParam StatutEntreprise statut) {
        return service.changerStatut(id, statut);
    }

    @PatchMapping("/entreprises/{id}/agences-multiples")
    @Operation(summary = "Module Agences : autorise ou non l'entreprise a gerer plusieurs agences (une seule par defaut)")
    public PlateformeService.EntrepriseResume autoriserAgencesMultiples(@PathVariable Long id,
                                                                      @RequestParam boolean autorise) {
        return service.autoriserAgencesMultiples(id, autorise);
    }
}
