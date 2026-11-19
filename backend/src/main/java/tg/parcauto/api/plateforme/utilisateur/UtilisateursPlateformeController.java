package tg.parcauto.api.plateforme.utilisateur;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.security.Autorisations;

/** Comptes de toutes les entreprises, geres par le super-administrateur. */
@RestController
@RequestMapping("/api/plateforme/utilisateurs")
@PreAuthorize(Autorisations.PLATEFORME)
@RequiredArgsConstructor
@Tag(name = "Plateforme - Utilisateurs")
public class UtilisateursPlateformeController {

    private final UtilisateursPlateformeService service;

    public record Activation(boolean actif) {
    }

    @GetMapping
    @Operation(summary = "Comptes de toutes les entreprises (recherche, entreprise, actifs)")
    public org.springframework.data.domain.Page<UtilisateursPlateformeService.Ligne> lister(@RequestParam(required = false) String q,
                                                           @RequestParam(required = false) Long entrepriseId,
                                                           @RequestParam(required = false) Boolean actif,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "50") int taille) {
        return service.lister(q, entrepriseId, actif, page, taille);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Modifie la fiche (e-mail, nom, prenom, telephone)")
    public void modifier(@PathVariable Long id, @Valid @RequestBody UtilisateursPlateformeService.Modification m) {
        service.modifier(id, m);
    }

    @PatchMapping("/{id}/activation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Active ou desactive le compte")
    public void activation(@PathVariable Long id, @RequestBody Activation a) {
        service.changerActivation(id, a.actif());
    }

    @PutMapping("/{id}/mot-de-passe")
    @Operation(summary = "Mot de passe provisoire (saisi ou genere), a changer a la prochaine connexion")
    public UtilisateursPlateformeService.MotDePasseProvisoire motDePasse(
            @PathVariable Long id, @Valid @RequestBody UtilisateursPlateformeService.MotDePasse m) {
        return service.reinitialiserMotDePasse(id, m.motDePasse());
    }

    @PostMapping("/{id}/deconnexion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Ferme toutes les sessions du compte")
    public void deconnecter(@PathVariable Long id) {
        service.deconnecter(id);
    }

    @DeleteMapping("/{id}/deux-facteurs")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Retire la double authentification (telephone perdu)")
    public void retirerDeuxFacteurs(@PathVariable Long id) {
        service.retirerDeuxFacteurs(id);
    }
}
