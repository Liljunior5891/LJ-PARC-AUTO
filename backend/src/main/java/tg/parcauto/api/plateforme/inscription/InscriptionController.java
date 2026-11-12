package tg.parcauto.api.plateforme.inscription;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleService;
import tg.parcauto.api.security.AuthController;
import tg.parcauto.api.security.SessionService;
import tg.parcauto.api.security.UtilisateurDetailsService;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.util.List;

/** Points d'entree ouverts aux visiteurs : catalogue des modules et creation de compte. */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "Inscription")
public class InscriptionController {

    private final InscriptionService inscriptionService;
    private final tg.parcauto.api.common.LimiteurRequetes limiteur;
    private final ModuleService moduleService;
    private final UtilisateurDetailsService utilisateurDetailsService;
    private final SessionService sessionService;

    @GetMapping("/modules")
    @Operation(summary = "Catalogue des modules proposes par la plateforme")
    public List<ModuleService.ModuleCatalogue> modules() {
        return moduleService.catalogue(null);
    }

    public record Reponse(String message) {
    }

    public record Confirmation(String jeton) {
    }

    /**
     * Premiere etape : un lien de confirmation part a l'adresse donnee. Reponse identique
     * que l'adresse ait deja un compte ou non.
     */
    @PostMapping("/inscription")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Demande la creation d'un compte entreprise : un lien de confirmation part par e-mail")
    public Reponse inscrire(@Valid @RequestBody InscriptionService.Demande demande,
                            jakarta.servlet.http.HttpServletRequest requete) {
        // Point public : quelques demandes par heure et par adresse IP, et par adresse e-mail.
        limiteur.exiger("inscription:" + requete.getRemoteAddr(), 5, java.time.Duration.ofHours(1),
                "Trop d'inscriptions depuis cette connexion : reessayez dans une heure");
        limiteur.exiger("inscription-email:" + demande.email().trim().toLowerCase(java.util.Locale.ROOT), 3,
                java.time.Duration.ofHours(1), "Trop de demandes pour cette adresse : reessayez dans une heure");
        inscriptionService.demander(demande);
        return new Reponse("Un e-mail de confirmation vient d'etre envoye a " + demande.email().trim()
                + ". Ouvrez le lien qu'il contient pour creer votre compte.");
    }

    /** Seconde etape : le lien est ouvert, le compte est cree et sa session ouverte. */
    @PostMapping("/inscription/confirmation")
    @Operation(summary = "Confirme l'adresse : cree le compte entreprise et ouvre la session de son administrateur")
    public AuthController.LoginResponse confirmer(@RequestBody Confirmation confirmation,
                                                  jakarta.servlet.http.HttpServletRequest requete) {
        limiteur.exiger("inscription-confirmation:" + requete.getRemoteAddr(), 20, java.time.Duration.ofHours(1),
                "Trop d'essais : reessayez dans une heure");
        String email = inscriptionService.confirmer(confirmation == null ? null : confirmation.jeton());
        var principal = (UtilisateurPrincipal) utilisateurDetailsService.loadUserByUsername(email);
        return sessionService.ouvrir(principal);
    }
}
