package tg.parcauto.api.agence.commission;

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

import java.time.LocalDate;
import java.util.List;

/** Rapprochement des commissions en attente avec les versements des operateurs. */
@RestController
@ModuleRequis(ModuleApplicatif.AGENCES)
@RequestMapping("/api/agences/rapprochement")
@RequiredArgsConstructor
@Tag(name = "Agences - rapprochement des commissions")
public class RapprochementController {

    private final RapprochementService service;

    public record Motif(@NotBlank @Size(max = 500) String motif) {
    }

    @GetMapping("/attentes")
    @PreAuthorize("@droitsAgence.a('COMMISSION_RAPPROCHER') or @droitsAgence.a('SOLDES_CONSULTER')")
    @Operation(summary = "Par operateur : commissions en attente de versement (nombre, montant, plus ancienne)")
    public List<RapprochementService.AttenteOperateur> attentes(@RequestParam(required = false) Long agenceId) {
        return service.attentes(agenceId);
    }

    @GetMapping("/commissions")
    @PreAuthorize("@droitsAgence.a('COMMISSION_RAPPROCHER')")
    @Operation(summary = "Commissions en attente d'un operateur dans une agence, des plus anciennes aux plus recentes")
    public Page<RapprochementService.CommissionAttente> enAttente(
            @RequestParam Long agenceId, @RequestParam Long serviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @PageableDefault(size = 50) Pageable pageable) {
        return service.enAttente(agenceId, serviceId, du, au, pageable);
    }

    @GetMapping("/versements")
    @PreAuthorize("@droitsAgence.a('COMMISSION_RAPPROCHER') or @droitsAgence.a('SOLDES_CONSULTER')")
    @Operation(summary = "Versements des operateurs, du plus recent au plus ancien")
    public Page<RapprochementService.VersementDto> versements(@RequestParam(required = false) Long agenceId,
                                                              @RequestParam(required = false) Long serviceId,
                                                              @PageableDefault(size = 30) Pageable pageable) {
        return service.historique(agenceId, serviceId, pageable);
    }

    @PostMapping("/versements")
    @PreAuthorize("@droitsAgence.a('COMMISSION_RAPPROCHER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistre un versement d'operateur et les commissions qu'il couvre (choisies ou toutes celles de la periode)")
    public RapprochementService.VersementDto enregistrer(@Valid @RequestBody RapprochementService.Versement v,
                                                         @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.enregistrer(v, principal.getNomComplet());
    }

    @PostMapping("/versements/{id}/annulation")
    @PreAuthorize("@droitsAgence.a('COMMISSION_RAPPROCHER')")
    @Operation(summary = "Annule un versement : ses commissions repassent en attente")
    public RapprochementService.VersementDto annuler(@PathVariable Long id, @Valid @RequestBody Motif motif,
                                                     @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.annuler(id, motif.motif(), principal.getNomComplet());
    }
}
