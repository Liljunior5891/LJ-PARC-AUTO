package tg.parcauto.api.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Comptes utilisateurs (chapitre 40).
 * <p>
 * Tout est reserve a l'administrateur, sauf son propre compte : chacun met a
 * jour son nom, son prenom, son telephone et son mot de passe lui-meme.
 */
@RestController
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
@Tag(name = "Utilisateurs")
public class UtilisateurController {

    private final UtilisateurService service;
    private final UtilisateurDetailsService detailsService;
    private final SessionService sessionService;

    @GetMapping("/profils")
    @Operation(summary = "Profils disponibles")
    public List<UtilisateurDto.ProfilRole> profils() {
        return service.profils();
    }

    @GetMapping
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Liste paginee des comptes")
    public Page<UtilisateurDto.Reponse> lister(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean actif,
            @PageableDefault(size = 20, sort = "nom", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.rechercher(q, role, actif, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Fiche d'un compte")
    public UtilisateurDto.Reponse detail(@PathVariable Long id) {
        return service.parId(id);
    }

    @PostMapping
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cree un compte")
    public UtilisateurDto.Reponse creer(@Valid @RequestBody UtilisateurDto.Creation demande) {
        return service.creer(demande);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Modifie un compte")
    public UtilisateurDto.Reponse modifier(@PathVariable Long id,
                                           @Valid @RequestBody UtilisateurDto.Modification demande,
                                           @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.modifier(id, demande, principal.getId());
    }

    @PatchMapping("/{id}/activation")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Active ou desactive un compte")
    public UtilisateurDto.Reponse changerActivation(@PathVariable Long id,
                                                    @RequestParam boolean actif,
                                                    @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.changerActivation(id, actif, principal.getId());
    }

    @PutMapping("/{id}/mot-de-passe")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Reinitialise le mot de passe d'un compte")
    public void reinitialiserMotDePasse(
            @PathVariable Long id,
            @Valid @RequestBody UtilisateurDto.ReinitialisationMotDePasse demande) {
        service.reinitialiserMotDePasse(id, demande.motDePasse());
    }

    @PutMapping("/{id}/pin")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Definit ou efface le code PIN de caisse d'un compte")
    public void definirPin(@PathVariable Long id, @Valid @RequestBody UtilisateurDto.DefinitionPin demande) {
        service.definirPin(id, demande.pin());
    }

    @GetMapping("/moi")
    @Operation(summary = "Son propre compte")
    public UtilisateurDto.Reponse moi(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.moi(principal.getId());
    }

    @PutMapping("/moi")
    @Operation(summary = "Met a jour son nom, son prenom et son telephone")
    public UtilisateurDto.Reponse mettreAJourSonProfil(@Valid @RequestBody UtilisateurDto.MiseAJourProfil demande,
                                                       @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.mettreAJourSonProfil(principal.getId(), demande);
    }

    @PutMapping("/moi/mot-de-passe")
    @Operation(summary = "Change son propre mot de passe ; renvoie une nouvelle session (les autres sont fermees)")
    public AuthController.LoginResponse changerSonMotDePasse(@Valid @RequestBody UtilisateurDto.ChangementMotDePasse demande,
                                                             @AuthenticationPrincipal UtilisateurPrincipal principal) {
        service.changerSonMotDePasse(principal.getId(), demande);
        UtilisateurPrincipal relu = (UtilisateurPrincipal) detailsService.loadUserByUsername(principal.getEmail());
        return sessionService.ouvrir(relu);
    }
}
