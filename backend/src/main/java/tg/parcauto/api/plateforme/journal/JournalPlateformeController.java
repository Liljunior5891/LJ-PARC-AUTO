package tg.parcauto.api.plateforme.journal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.security.Autorisations;

import java.time.LocalDate;
import java.util.List;

/** Journal d'audit general : ce qui s'est fait dans tout le systeme, reserve au super-admin. */
@RestController
@RequestMapping("/api/plateforme/journal")
@RequiredArgsConstructor
@PreAuthorize(Autorisations.PLATEFORME)
@Tag(name = "Plateforme - journal d'audit")
public class JournalPlateformeController {

    private final JournalPlateformeService service;

    @GetMapping
    @Operation(summary = "Actions de toutes les entreprises et de la plateforme, les plus recentes d'abord")
    public Page<JournalPlateformeService.Ligne> lister(
            @RequestParam(required = false) String entreprise,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String recherche,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long total) {
        return service.lister(new JournalPlateformeService.Filtres(entreprise, module, action, du, au, recherche), page, size, total);
    }

    @GetMapping("/synthese")
    @Operation(summary = "Rapport de la periode : volumes, modules, entreprises et auteurs les plus actifs, activite par jour")
    public JournalPlateformeService.Synthese synthese(
            @RequestParam(required = false) String entreprise,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String recherche) {
        return service.synthese(new JournalPlateformeService.Filtres(entreprise, module, action, du, au, recherche));
    }

    @GetMapping("/entreprises")
    @Operation(summary = "Entreprises, pour le filtre du journal")
    public List<JournalPlateformeService.Choix> entreprises() {
        return service.entreprises();
    }
}
