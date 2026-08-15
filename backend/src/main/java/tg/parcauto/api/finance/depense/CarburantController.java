package tg.parcauto.api.finance.depense;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/carburant")
@RequiredArgsConstructor
@Tag(name = "Carburant")
public class CarburantController {

    private final CarburantService service;

    @GetMapping("/camions/{camionId}")
    @Operation(summary = "Pleins, consommation moyenne et cout au kilometre d'un camion")
    public CarburantDto.Synthese synthese(
            @PathVariable Long camionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        LocalDate finRetenue = fin == null ? LocalDate.now() : fin;
        LocalDate debutRetenu = debut == null ? finRetenue.withDayOfYear(1) : debut;
        return service.synthese(camionId, debutRetenu, finRetenue);
    }
}
