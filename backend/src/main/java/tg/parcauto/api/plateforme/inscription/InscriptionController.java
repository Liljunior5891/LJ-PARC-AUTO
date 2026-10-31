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
    private final ModuleService moduleService;
    private final UtilisateurDetailsService utilisateurDetailsService;
    private final SessionService sessionService;

    @GetMapping("/modules")
    @Operation(summary = "Catalogue des modules proposes par la plateforme")
    public List<ModuleService.ModuleCatalogue> modules() {
        return moduleService.catalogue(null);
    }

    /** L'administrateur cree est connecte dans la foulee : la reponse est celle d'une connexion. */
    @PostMapping("/inscription")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cree un compte entreprise et son administrateur, puis ouvre sa session")
    public AuthController.LoginResponse inscrire(@Valid @RequestBody InscriptionService.Demande demande) {
        String email = inscriptionService.inscrire(demande);
        var principal = (UtilisateurPrincipal) utilisateurDetailsService.loadUserByUsername(email);
        return sessionService.ouvrir(principal);
    }
}
