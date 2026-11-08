package tg.parcauto.api.agence.tresorerie;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;

import java.time.LocalDate;
import java.util.List;

/** Soldes, registre des mouvements et comptes des agences. */
@RestController
@ModuleRequis(ModuleApplicatif.AGENCES)
@RequestMapping("/api/agences")
@RequiredArgsConstructor
@Tag(name = "Agences - soldes et tresorerie")
public class SoldeController {

    private final SoldeService service;

    @GetMapping("/soldes")
    @PreAuthorize("@droitsAgence.a('SOLDES_CONSULTER')")
    @Operation(summary = "Soldes et tresorerie d'une agence, ou de toutes les agences visibles")
    public SoldeService.Etat soldes(@RequestParam(required = false) Long agenceId) {
        return service.etat(agenceId);
    }

    @GetMapping("/comptes/{id}/registre")
    @PreAuthorize("@droitsAgence.a('SOLDES_CONSULTER')")
    @Operation(summary = "Registre des mouvements d'un compte, du plus recent au plus ancien")
    public Page<SoldeService.LigneDto> registre(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @PageableDefault(size = 30) Pageable pageable) {
        return service.registre(id, du, au, pageable);
    }

    @GetMapping("/comptes/{id}/solde-au")
    @PreAuthorize("@droitsAgence.a('SOLDES_CONSULTER')")
    @Operation(summary = "Solde d'un compte a la fin d'une journee, reconstitue a partir du registre")
    public SoldeService.SoldeAuJour soldeAu(@PathVariable Long id,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour) {
        return service.soldeAu(id, jour);
    }

    @PostMapping("/{id:\\d+}/ouverture")
    @PreAuthorize("@droitsAgence.a('SOLDES_INITIAUX')")
    @Operation(summary = "Soldes d'ouverture des comptes de l'agence, en une ecriture horodatee")
    public SoldeService.Etat ouvrir(@PathVariable Long id, @Valid @RequestBody SoldeService.Ouverture ouverture) {
        return service.ouvrir(id, ouverture);
    }

    @PostMapping("/{id:\\d+}/verification")
    @PreAuthorize("@droitsAgence.a('SOLDES_INITIAUX')")
    @Operation(summary = "Recalcule chaque solde a partir du registre et corrige un ecart")
    public List<SoldeService.Verification> verifier(@PathVariable Long id) {
        return service.verifier(id);
    }

    @PostMapping("/{id:\\d+}/comptes")
    @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajoute un compte bancaire ou un fonds d'exploitation a l'agence")
    public SoldeService.CompteDto creerCompte(@PathVariable Long id, @Valid @RequestBody SoldeService.RequeteCompte r) {
        return service.creerCompte(id, r);
    }

    @PutMapping("/comptes/{id}")
    @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")
    @Operation(summary = "Modifie un compte : decouvert tolere, blocage, banque")
    public SoldeService.CompteDto modifierCompte(@PathVariable Long id, @Valid @RequestBody SoldeService.RequeteCompte r) {
        return service.modifierCompte(id, r);
    }
}
