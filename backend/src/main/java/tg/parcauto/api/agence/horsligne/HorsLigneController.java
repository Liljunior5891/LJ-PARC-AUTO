package tg.parcauto.api.agence.horsligne;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.util.List;
import java.util.Map;

/** Operations des caissiers saisies sans connexion, et leurs conflits. */
@RestController
@RequestMapping("/api/agences/hors-ligne")
@RequiredArgsConstructor
@Tag(name = "Agences - hors ligne")
public class HorsLigneController {

    private final HorsLigneService service;

    @PostMapping("/synchronisation")
    @Operation(summary = "Envoie les operations saisies sans connexion ; une operation refusee devient un conflit")
    public List<HorsLigneService.Resultat> synchroniser(@Valid @RequestBody List<HorsLigneService.@Valid OperationEnvoyee> operations,
                                                       @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.synchroniser(operations, principal);
    }

    @GetMapping("/conflits")
    @PreAuthorize("@droitsAgence.a('CAISSE_GERER')")
    @Operation(summary = "Operations hors ligne refusees, a reprendre ou abandonner")
    public List<HorsLigneService.ConflitDto> conflits() {
        return service.conflits();
    }

    @GetMapping("/conflits/nombre")
    @PreAuthorize("@droitsAgence.a('CAISSE_GERER')")
    @Operation(summary = "Nombre de conflits a traiter")
    public Map<String, Long> nombre() {
        return Map.of("nombre", service.nombreConflits());
    }

    @PostMapping("/conflits/{id}/reprise")
    @PreAuthorize("@droitsAgence.a('CAISSE_GERER')")
    @Operation(summary = "Rejoue l'operation une fois la cause levee")
    public HorsLigneService.ConflitDto reprendre(@PathVariable Long id, @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.reprendre(id, principal);
    }

    @PostMapping("/conflits/{id}/abandon")
    @PreAuthorize("@droitsAgence.a('CAISSE_GERER')")
    @Operation(summary = "Regle le conflit hors du logiciel, avec une note")
    public HorsLigneService.ConflitDto abandonner(@PathVariable Long id, @Valid @RequestBody HorsLigneService.Abandon abandon,
                                                  @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.abandonner(id, abandon, principal);
    }
}
