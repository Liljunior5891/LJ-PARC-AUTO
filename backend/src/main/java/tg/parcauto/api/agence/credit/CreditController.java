package tg.parcauto.api.agence.credit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.util.List;

/** Credits, dettes, apports et avances des agences, et leurs remboursements. */
@RestController
@ModuleRequis(ModuleApplicatif.AGENCES)
@RequestMapping("/api/agences/credits")
@RequiredArgsConstructor
@Tag(name = "Agences - credits et dettes")
public class CreditController {

    private final CreditService service;

    public record Motif(@NotBlank @Size(max = 500) String motif) {
    }

    @GetMapping
    @PreAuthorize("@droitsAgence.a('CREDIT_GERER') or @droitsAgence.a('RAPPORTS_COMPLETS')")
    @Operation(summary = "Credits et dettes des agences visibles")
    public List<CreditService.CreditDto> lister(@RequestParam(required = false) Long agenceId,
                                                @RequestParam(required = false) CreditAgence.Statut statut) {
        return service.lister(agenceId, statut);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@droitsAgence.a('CREDIT_GERER') or @droitsAgence.a('RAPPORTS_COMPLETS')")
    @Operation(summary = "Un credit, son echeancier et ses remboursements")
    public CreditService.CreditDto detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @PreAuthorize("@droitsAgence.a('CREDIT_GERER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistre un credit recu, un apport, une avance accordee ou une dette")
    public CreditService.CreditDto creer(@Valid @RequestBody CreditService.Requete r, @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.creer(r, principal);
    }

    @PostMapping("/{id}/remboursements")
    @PreAuthorize("@droitsAgence.a('CREDIT_GERER')")
    @Operation(summary = "Enregistre un remboursement : capital et interets distingues")
    public CreditService.CreditDto rembourser(@PathVariable Long id, @Valid @RequestBody CreditService.DemandeRemboursement d,
                                              @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.rembourser(id, d, principal);
    }

    @PostMapping("/remboursements/{id}/annulation")
    @PreAuthorize("@droitsAgence.a('CREDIT_GERER')")
    @Operation(summary = "Annule un remboursement par l'ecriture inverse")
    public CreditService.CreditDto annulerRemboursement(@PathVariable Long id, @Valid @RequestBody Motif motif,
                                                        @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.annulerRemboursement(id, motif.motif(), principal);
    }
}
