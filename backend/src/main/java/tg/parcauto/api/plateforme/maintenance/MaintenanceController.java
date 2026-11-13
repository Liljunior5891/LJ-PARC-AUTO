package tg.parcauto.api.plateforme.maintenance;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.security.Autorisations;

import java.time.Instant;
import java.util.List;

/**
 * Maintenance et mises a jour : la console de l'exploitant (programmer, terminer,
 * publier une version) et ce que chaque navigateur lit sans compte (annonces,
 * maintenance en cours, version en vigueur).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Plateforme - maintenance et versions")
public class MaintenanceController {

    private final MaintenanceService maintenances;
    private final VersionPlateformeService versions;

    /** Etat lu par tous les navigateurs, connectes ou non (page de connexion, boutique publique). */
    public record EtatPlateforme(Instant maintenant, List<MaintenanceService.MaintenanceVue> maintenances,
                                 VersionPlateformeService.VersionVue version) {
    }

    // ------------------------------------------------------------- public

    @GetMapping("/api/public/etat-plateforme")
    @Operation(summary = "Maintenances annoncees ou en cours et version en vigueur")
    public EtatPlateforme etat() {
        return new EtatPlateforme(Instant.now(), maintenances.annonces().stream().map(MaintenanceController::publique).toList(),
                versions.courante());
    }

    @GetMapping("/api/public/nouveautes")
    @Operation(summary = "Notes des dernieres versions publiees")
    public List<VersionPlateformeService.VersionVue> nouveautes(@RequestParam(defaultValue = "20") int limite) {
        return versions.publiees(limite).stream().map(MaintenanceController::publique).toList();
    }

    /** Hors de la console, l'auteur n'est pas montre. */
    private static MaintenanceService.MaintenanceVue publique(MaintenanceService.MaintenanceVue m) {
        return new MaintenanceService.MaintenanceVue(m.id(), m.message(), m.modules(), m.annonceLe(), m.debut(),
                m.finPrevue(), m.termineeLe(), null, m.statut(), null, null);
    }

    private static VersionPlateformeService.VersionVue publique(VersionPlateformeService.VersionVue v) {
        return new VersionPlateformeService.VersionVue(v.id(), v.numero(), v.titre(), v.notes(), v.rechargement(),
                v.publieeLe(), null);
    }

    // ------------------------------------------------------------- console

    @GetMapping("/api/plateforme/maintenances")
    @PreAuthorize(Autorisations.PLATEFORME)
    public List<MaintenanceService.MaintenanceVue> maintenances() {
        return maintenances.liste();
    }

    @PostMapping("/api/plateforme/maintenances")
    @PreAuthorize(Autorisations.PLATEFORME)
    @Operation(summary = "Programme une maintenance sur un ou plusieurs modules")
    public MaintenanceService.MaintenanceVue programmer(@Valid @RequestBody MaintenanceService.Saisie saisie) {
        return maintenances.programmer(saisie);
    }

    @PutMapping("/api/plateforme/maintenances/{id}")
    @PreAuthorize(Autorisations.PLATEFORME)
    public MaintenanceService.MaintenanceVue modifier(@PathVariable Long id,
                                                      @Valid @RequestBody MaintenanceService.Saisie saisie) {
        return maintenances.modifier(id, saisie);
    }

    @PostMapping("/api/plateforme/maintenances/{id}/terminer")
    @PreAuthorize(Autorisations.PLATEFORME)
    @Operation(summary = "Termine tout de suite une maintenance en cours")
    public MaintenanceService.MaintenanceVue terminer(@PathVariable Long id) {
        return maintenances.terminer(id);
    }

    @PostMapping("/api/plateforme/maintenances/{id}/annuler")
    @PreAuthorize(Autorisations.PLATEFORME)
    public MaintenanceService.MaintenanceVue annuler(@PathVariable Long id) {
        return maintenances.annuler(id);
    }

    @GetMapping("/api/plateforme/versions")
    @PreAuthorize(Autorisations.PLATEFORME)
    public List<VersionPlateformeService.VersionVue> versions() {
        return versions.toutes();
    }

    @PostMapping("/api/plateforme/versions")
    @PreAuthorize(Autorisations.PLATEFORME)
    @Operation(summary = "Publie une version : ses notes et, au besoin, le rechargement des navigateurs")
    public VersionPlateformeService.VersionVue publier(@Valid @RequestBody VersionPlateformeService.Saisie saisie) {
        return versions.publier(saisie);
    }

    @PutMapping("/api/plateforme/versions/{id}")
    @PreAuthorize(Autorisations.PLATEFORME)
    public VersionPlateformeService.VersionVue modifierVersion(@PathVariable Long id,
                                                               @Valid @RequestBody VersionPlateformeService.Saisie saisie) {
        return versions.modifier(id, saisie);
    }

    @DeleteMapping("/api/plateforme/versions/{id}")
    @PreAuthorize(Autorisations.PLATEFORME)
    public ResponseEntity<Void> supprimerVersion(@PathVariable Long id) {
        versions.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
