package tg.parcauto.api.plateforme.parametres;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.security.Autorisations;

/** Parametrage general de la plateforme par le super-administrateur. */
@RestController
@RequestMapping("/api/plateforme/parametres")
@PreAuthorize(Autorisations.PLATEFORME)
@RequiredArgsConstructor
@Tag(name = "Plateforme - Parametrage")
public class ParametresPlateformeController {

    private final ParametresPlateformeService service;

    @GetMapping
    @Operation(summary = "Reglages generaux de la plateforme")
    public ParametresPlateformeService.Vue lire() {
        return service.lire();
    }

    @PutMapping
    @Operation(summary = "Modifie les reglages generaux de la plateforme")
    public ParametresPlateformeService.Vue modifier(@RequestBody ParametresPlateformeService.Modification m) {
        return service.modifier(m);
    }
}
