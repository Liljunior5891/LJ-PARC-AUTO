package tg.parcauto.api.plateforme.assistance;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.common.audit.JournalActiviteService;
import tg.parcauto.api.plateforme.entreprise.Entreprise;
import tg.parcauto.api.plateforme.entreprise.EntrepriseRepository;
import tg.parcauto.api.security.AuthController;
import tg.parcauto.api.security.Autorisations;
import tg.parcauto.api.security.SessionService;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.util.Map;

/**
 * Assistance a distance : le super-administrateur ouvre l'espace d'une entreprise
 * pour voir ses donnees (consultation) ou corriger une erreur (intervention).
 * <p>
 * La session dure deux heures au plus. L'ouverture, avec son motif, et la fin sont
 * ecrites dans le journal de la plateforme et dans celui de l'entreprise ; pendant
 * l'assistance, chaque action est signee « Assistance Nova (nom) ».
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Plateforme - Assistance")
public class AssistanceController {

    private final EntrepriseRepository entreprises;
    private final SessionService sessions;
    private final JournalActiviteService journal;

    public record Ouverture(@NotBlank @Pattern(regexp = "CONSULTATION|INTERVENTION") String mode,
                            @NotBlank @Size(max = 300) String motif) {
    }

    @PostMapping("/api/plateforme/entreprises/{id}/assistance")
    @PreAuthorize(Autorisations.PLATEFORME)
    @Operation(summary = "Ouvre l'espace d'une entreprise en assistance (consultation ou intervention)")
    public AuthController.LoginResponse ouvrir(@PathVariable Long id, @Valid @RequestBody Ouverture o,
                                               @AuthenticationPrincipal UtilisateurPrincipal superAdmin) {
        Entreprise e = entreprises.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Entreprise", id));
        String mode = o.mode();
        String motif = o.motif().trim();
        String libelleMode = "CONSULTATION".equals(mode) ? "consultation" : "intervention";
        journal.plateforme("ASSISTANCE_DEBUT", "Entreprise", e.getNom(), e.getId(),
                "Assistance (" + libelleMode + ") ouverte dans " + e.getNom() + " : " + motif, null,
                Map.of("mode", mode, "motif", motif));
        journal.administration(e.getId(), "ASSISTANCE_DEBUT", "Entreprise", e.getNom(), e.getId(),
                "L'assistance Nova (" + superAdmin.getNomComplet() + ") ouvre votre espace en " + libelleMode
                        + " : " + motif, null, Map.of("mode", mode, "motif", motif));
        return sessions.ouvrirAssistance(superAdmin, e.getId(), e.getNom(), mode);
    }

    @PostMapping("/api/auth/assistance/fin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Termine la session d'assistance (le jeton expire de lui-meme ; la console reprend la sienne)")
    public void terminer(@AuthenticationPrincipal UtilisateurPrincipal p) {
        if (p == null || !p.enAssistance()) {
            return;
        }
        journal.administration(p.getEntrepriseId(), "ASSISTANCE_FIN", "Entreprise", p.getEntrepriseNom(),
                p.getEntrepriseId(), p.getNomComplet() + " quitte votre espace", null, null);
    }
}
