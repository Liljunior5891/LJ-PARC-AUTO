package tg.parcauto.api.parametrage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tg.parcauto.api.security.Autorisations;

/**
 * Parametrage de l'entreprise (chapitre 39).
 * <p>
 * La modification est reservee a l'administrateur ; la lecture est ouverte a
 * tous les comptes, chaque ecran pouvant avoir besoin du nom de l'entreprise.
 */
@RestController
@RequestMapping("/api/parametrage")
@RequiredArgsConstructor
@Tag(name = "Parametrage")
public class SocieteController {

    private final SocieteService service;

    @GetMapping("/societe")
    @Operation(summary = "Informations de l'entreprise")
    public SocieteDto.Reponse lire() {
        return service.lire();
    }

    @PutMapping("/societe")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Enregistre les informations de l'entreprise")
    public SocieteDto.Reponse enregistrer(@Valid @RequestBody SocieteDto.Requete requete) {
        return service.enregistrer(requete);
    }

    @PostMapping("/societe/logo")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Remplace le logo de l'entreprise")
    public SocieteDto.Reponse remplacerLogo(@RequestParam("fichier") MultipartFile fichier) {
        return service.remplacerLogo(fichier);
    }

    @DeleteMapping("/societe/logo")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Retire le logo de l'entreprise")
    public SocieteDto.Reponse supprimerLogo() {
        return service.supprimerLogo();
    }
}
