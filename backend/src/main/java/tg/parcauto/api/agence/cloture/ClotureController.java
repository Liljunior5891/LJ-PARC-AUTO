package tg.parcauto.api.agence.cloture;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.time.LocalDate;
import java.util.List;

/** Cloture journaliere des agences : preparation, soumission, validation, rejet et reouverture. */
@RestController
@ModuleRequis(ModuleApplicatif.AGENCES)
@RequestMapping("/api/agences/clotures")
@RequiredArgsConstructor
@Tag(name = "Agences - cloture journaliere")
public class ClotureController {

    private final ClotureService service;

    public record Motif(@NotBlank @Size(max = 500) String motif) {
    }

    @GetMapping("/preparation")
    @PreAuthorize("@droitsAgence.a('CLOTURE_SOUMETTRE') or @droitsAgence.a('CLOTURE_VALIDER')")
    @Operation(summary = "Comptes a controler et soldes theoriques de la journee, cloture existante")
    public ClotureService.Preparation preparer(@RequestParam Long agenceId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journee) {
        return service.preparer(agenceId, journee);
    }

    @GetMapping
    @PreAuthorize("@droitsAgence.a('CLOTURE_SOUMETTRE') or @droitsAgence.a('CLOTURE_VALIDER')")
    @Operation(summary = "Clotures des agences visibles, de la plus recente a la plus ancienne")
    public Page<ClotureService.ClotureDto> lister(@RequestParam(required = false) Long agenceId,
                                                  @PageableDefault(size = 30) Pageable pageable) {
        return service.lister(agenceId, pageable);
    }

    @GetMapping("/a-faire")
    @Operation(summary = "Journees avec activite pas encore cloturees (60 derniers jours)")
    public List<ClotureService.AFaire> aFaire(@RequestParam(required = false) Long agenceId) {
        return service.aFaire(agenceId);
    }

    @PostMapping
    @PreAuthorize("@droitsAgence.a('CLOTURE_SOUMETTRE')")
    @Operation(summary = "Soumet la cloture : soldes reels et justification des ecarts")
    public ClotureService.ClotureDto soumettre(@Valid @RequestBody ClotureService.Soumission soumission,
                                               @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.soumettre(soumission, principal);
    }

    @PostMapping("/{id}/validation")
    @PreAuthorize("@droitsAgence.a('CLOTURE_VALIDER')")
    @Operation(summary = "Valide la cloture et verrouille la journee")
    public ClotureService.ClotureDto valider(@PathVariable Long id, @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.valider(id, principal);
    }

    @PostMapping("/{id}/rejet")
    @PreAuthorize("@droitsAgence.a('CLOTURE_VALIDER')")
    @Operation(summary = "Rejette la cloture soumise, avec motif")
    public ClotureService.ClotureDto rejeter(@PathVariable Long id, @Valid @RequestBody Motif motif,
                                             @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.rejeter(id, motif.motif(), principal);
    }

    @PostMapping("/{id}/reouverture")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rouvre une journee cloturee (administrateur, motif trace)")
    public ClotureService.ClotureDto rouvrir(@PathVariable Long id, @Valid @RequestBody Motif motif,
                                             @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.rouvrir(id, motif.motif(), principal);
    }
}
