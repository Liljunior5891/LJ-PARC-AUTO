package tg.parcauto.api.common.audit;

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

import java.time.LocalDate;

/** Journal d'audit des modules de l'entreprise, reserve a l'administrateur. */
@RestController
@RequestMapping("/api/journal-audit")
@RequiredArgsConstructor
@Tag(name = "Journal d'audit")
@PreAuthorize("hasRole('ADMIN')")
public class JournalActiviteController {

    private final JournalActiviteService service;

    @GetMapping
    @Operation(summary = "Qui a fait quoi, quand, avec les valeurs avant et apres")
    public Page<JournalActiviteService.LigneDto> lister(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String objet,
            @RequestParam(required = false) Long objetId,
            @RequestParam(required = false) Long auteurId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String recherche,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.lister(new JournalActiviteService.Filtres(module, action, objet, auteurId, du, au, recherche, objetId), page, size);
    }

    @GetMapping("/referentiels")
    @Operation(summary = "Types de donnees et auteurs presents dans le journal, pour les filtres")
    public JournalActiviteService.Referentiels referentiels() {
        return service.referentiels();
    }
}
