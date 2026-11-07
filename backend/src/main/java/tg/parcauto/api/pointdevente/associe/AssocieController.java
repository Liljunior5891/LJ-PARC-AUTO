package tg.parcauto.api.pointdevente.associe;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.Autorisations;

import java.time.Instant;
import java.util.List;

/** Associes du point de vente : rattachements (administrateur) et part des benefices (chaque associe voit la sienne). */
@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@tg.parcauto.api.plateforme.module.OuvertAuxAssocies
@RequestMapping("/api/pdv/associes")
@PreAuthorize(Autorisations.ADMINISTRATION)
@RequiredArgsConstructor
@Tag(name = "Point de vente - associes")
public class AssocieController {

    private final AssocieService service;

    @GetMapping
    @Operation(summary = "Comptes au profil associe et leurs rattachements")
    public List<AssocieService.Associe> associes() {
        return service.associes();
    }

    @PutMapping("/{id}/parts")
    @Operation(summary = "Remplace les produits et categories d'un associe, avec leurs pourcentages")
    public AssocieService.Associe remplacer(@PathVariable Long id, @Valid @RequestBody AssocieService.Parts parts) {
        return service.remplacerParts(id, parts.parts());
    }

    @GetMapping("/benefices")
    @PreAuthorize("hasAnyRole('ADMIN','ASSOCIE')")
    @Operation(summary = "Part de chaque associe sur la marge de ses produits ; un associe ne voit que la sienne")
    public AssocieService.Benefices benefices(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fin,
            @org.springframework.security.core.annotation.AuthenticationPrincipal tg.parcauto.api.security.UtilisateurPrincipal principal) {
        return service.benefices(debut, fin, principal);
    }
}
