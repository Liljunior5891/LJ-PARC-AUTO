package tg.parcauto.api.agence.caisse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

/** Caisses des caissiers d'agence : ouverture, fermeture, etat et historique. */
@RestController
@ModuleRequis(ModuleApplicatif.AGENCES)
@RequestMapping("/api/agences/caisses")
@RequiredArgsConstructor
@Tag(name = "Agences - caisses des caissiers")
public class SessionCaisseController {

    private final SessionCaisseService service;

    @GetMapping
    @PreAuthorize("@droitsAgence.a('CAISSE_GERER')")
    @Operation(summary = "Caissiers de l'agence, etat de leur caisse et solde de la caisse generale")
    public SessionCaisseService.EtatAgence etat(@RequestParam Long agenceId) {
        return service.etat(agenceId);
    }

    @GetMapping("/ma-session")
    @Operation(summary = "Caisse ouverte de l'utilisateur dans l'agence ; 204 s'il n'en a pas")
    public ResponseEntity<SessionCaisseService.SessionDto> maSession(@RequestParam Long agenceId,
                                                                     @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.maSession(agenceId, principal).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/sessions")
    @PreAuthorize("@droitsAgence.a('CAISSE_GERER')")
    @Operation(summary = "Historique des sessions de caisse")
    public Page<SessionCaisseService.SessionDto> historique(@RequestParam(required = false) Long agenceId,
                                                            @PageableDefault(size = 30) Pageable pageable) {
        return service.historique(agenceId, pageable);
    }

    @GetMapping("/sessions/{id}")
    @PreAuthorize("@droitsAgence.a('CAISSE_GERER')")
    @Operation(summary = "Detail d'une session de caisse")
    public SessionCaisseService.SessionDto detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping("/sessions")
    @PreAuthorize("@droitsAgence.a('CAISSE_GERER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ouvre la caisse d'un caissier : fond compte par coupure, pris sur la caisse generale")
    public SessionCaisseService.SessionDto ouvrir(@Valid @RequestBody SessionCaisseService.Ouverture ouverture,
                                                  @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.ouvrir(ouverture, principal);
    }

    @PostMapping("/sessions/{id}/soumission")
    @Operation(summary = "Le caissier compte son tiroir et soumet la fermeture de sa caisse au superviseur")
    public SessionCaisseService.SessionDto soumettre(@PathVariable Long id,
                                                     @Valid @RequestBody SessionCaisseService.Fermeture fermeture,
                                                     @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.soumettre(id, fermeture, principal);
    }

    @PostMapping("/sessions/{id}/rejet")
    @PreAuthorize("@droitsAgence.a('CAISSE_GERER')")
    @Operation(summary = "Refuse le comptage soumis : la caisse est rouverte au caissier")
    public SessionCaisseService.SessionDto rejeter(@PathVariable Long id, @Valid @RequestBody SessionCaisseService.Rejet rejet,
                                                   @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.rejeter(id, rejet, principal);
    }

    @PostMapping("/sessions/{id}/fermeture")
    @PreAuthorize("@droitsAgence.a('CAISSE_GERER')")
    @Operation(summary = "Valide (caisse soumise) ou ferme la caisse : comptage, ecart justifie, remise a la caisse generale")
    public SessionCaisseService.SessionDto fermer(@PathVariable Long id,
                                                  @Valid @RequestBody SessionCaisseService.Fermeture fermeture,
                                                  @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.fermer(id, fermeture, principal);
    }
}
