package tg.parcauto.api.presence;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Gestion de presence : employes et PIN, lieux et bornes, horaire, journee et
 * historique. L'administrateur et le gerant voient tout ; le gestionnaire de
 * parc et le superviseur d'agence, les lieux dont ils sont responsables.
 */
@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
@ModuleRequis(ModuleApplicatif.PRESENCE)
@Tag(name = "Presence - gestion")
public class PresenceController {

    private static final String GESTION = "hasAnyRole('ADMIN','GERANT','GESTIONNAIRE_PARC','SUPERVISEUR_AGENCE')";
    /**
     * Creer un employe, le modifier (date d'embauche, activation), regenerer son PIN et relier un
     * compte : administrateur et gerant seulement. Un responsable de lieu (parc, agence) suit les
     * pointages mais ne cree ni n'efface de presences.
     */
    private static final String ADMINISTRATION = "hasAnyRole('ADMIN','GERANT')";

    private final PresenceService presence;
    private final PointageService pointage;

    // ------------------------------------------------------------- suivi

    @GetMapping("/jour")
    @PreAuthorize(GESTION)
    @Operation(summary = "Journee : arrivees, retards, absents")
    public PointageService.Jour jour(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                     @RequestParam(required = false) Long lieuId,
                                     @AuthenticationPrincipal UtilisateurPrincipal p) {
        return pointage.jour(date == null ? LocalDate.now(PointageService.FUSEAU) : date, lieuId, p);
    }

    @GetMapping("/employes/{id}/historique")
    @PreAuthorize(GESTION)
    @Operation(summary = "Mois d'un employe, jour par jour, avec ses totaux")
    public PointageService.Historique historique(@PathVariable Long id, @RequestParam(required = false) String mois,
                                                 @AuthenticationPrincipal UtilisateurPrincipal p) {
        return pointage.historique(id, mois == null || mois.isBlank() ? YearMonth.now(PointageService.FUSEAU) : YearMonth.parse(mois), p);
    }

    // ------------------------------------------------------------- employes

    @GetMapping("/employes")
    @PreAuthorize(GESTION)
    public List<PresenceService.EmployeReponse> employes(@AuthenticationPrincipal UtilisateurPrincipal p) {
        return presence.listerEmployes(p);
    }

    @GetMapping("/employes/{id}")
    @PreAuthorize(GESTION)
    public PresenceService.EmployeReponse employe(@PathVariable Long id, @AuthenticationPrincipal UtilisateurPrincipal p) {
        return presence.employe(id, p);
    }

    @PostMapping("/employes")
    @PreAuthorize(ADMINISTRATION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cree un employe ; son PIN est rendu une seule fois")
    public PresenceService.EmployeAvecPin creerEmploye(@Valid @RequestBody PresenceService.EmployeRequete r,
                                                       @AuthenticationPrincipal UtilisateurPrincipal p) {
        return presence.creerEmploye(r, p);
    }

    @PutMapping("/employes/{id}")
    @PreAuthorize(ADMINISTRATION)
    public PresenceService.EmployeReponse modifierEmploye(@PathVariable Long id, @Valid @RequestBody PresenceService.EmployeRequete r,
                                                          @AuthenticationPrincipal UtilisateurPrincipal p) {
        return presence.modifierEmploye(id, r, p);
    }

    @PostMapping("/employes/{id}/pin")
    @PreAuthorize(ADMINISTRATION)
    @Operation(summary = "Donne un nouveau PIN a l'employe ; l'ancien ne marche plus")
    public PresenceService.EmployeAvecPin reinitialiserPin(@PathVariable Long id, @AuthenticationPrincipal UtilisateurPrincipal p) {
        return presence.reinitialiserPin(id, p);
    }

    @GetMapping("/comptes-convertibles")
    @PreAuthorize(ADMINISTRATION)
    @Operation(summary = "Comptes utilisateurs pas encore relies a un employe")
    public List<PresenceService.CompteConvertible> comptesConvertibles() {
        return presence.comptesConvertibles();
    }

    // ------------------------------------------------------------- lieux et bornes

    @GetMapping("/responsables-possibles")
    @PreAuthorize(ADMINISTRATION)
    @Operation(summary = "Gestionnaires de parc et superviseurs d'agence, responsables possibles d'un lieu")
    public List<PresenceService.CompteConvertible> responsablesPossibles() {
        return presence.responsablesPossibles();
    }

    @GetMapping("/lieux")
    @PreAuthorize(GESTION)
    public List<PresenceService.LieuReponse> lieux(@AuthenticationPrincipal UtilisateurPrincipal p) {
        return presence.listerLieux(p);
    }

    @PostMapping("/lieux")
    @PreAuthorize(ADMINISTRATION)
    @ResponseStatus(HttpStatus.CREATED)
    public PresenceService.LieuReponse creerLieu(@Valid @RequestBody PresenceService.LieuRequete r) {
        return presence.creerLieu(r);
    }

    @PutMapping("/lieux/{id}")
    @PreAuthorize(ADMINISTRATION)
    public PresenceService.LieuReponse modifierLieu(@PathVariable Long id, @Valid @RequestBody PresenceService.LieuRequete r) {
        return presence.modifierLieu(id, r);
    }

    @PostMapping("/lieux/{id}/secret")
    @PreAuthorize(ADMINISTRATION)
    @Operation(summary = "Renouvelle le secret du code QR : les codes deja affiches ne marchent plus")
    public PresenceService.LieuReponse renouvelerSecret(@PathVariable Long id) {
        return presence.renouvelerSecret(id);
    }

    @PostMapping("/bornes")
    @PreAuthorize(ADMINISTRATION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajoute une borne : un code a 6 chiffres a saisir sur la tablette")
    public PresenceService.BorneReponse creerBorne(@Valid @RequestBody PresenceService.BorneRequete r) {
        return presence.creerBorne(r);
    }

    @PostMapping("/bornes/{id}/appairage")
    @PreAuthorize(ADMINISTRATION)
    @Operation(summary = "Nouveau code d'appairage : l'ancienne tablette est deconnectee")
    public PresenceService.BorneReponse reappairer(@PathVariable Long id) {
        return presence.reappairer(id);
    }

    @PostMapping("/bornes/{id}/activation")
    @PreAuthorize(ADMINISTRATION)
    public PresenceService.BorneReponse basculerBorne(@PathVariable Long id) {
        return presence.basculerBorne(id);
    }

    // ------------------------------------------------------------- horaire

    @GetMapping("/reglage")
    @PreAuthorize(GESTION)
    public PresenceService.ReglageReponse reglage() {
        return presence.reglage();
    }

    @PutMapping("/reglage")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Horaire de l'entreprise : heure d'arrivee, tolerance, jours travailles, heure limite")
    public PresenceService.ReglageReponse modifierReglage(@Valid @RequestBody PresenceService.ReglageRequete r) {
        return presence.modifierReglage(r);
    }
}
