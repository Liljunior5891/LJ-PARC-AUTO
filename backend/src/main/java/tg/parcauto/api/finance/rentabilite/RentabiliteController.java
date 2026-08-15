package tg.parcauto.api.finance.rentabilite;

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
@RequestMapping("/api/rentabilite")
@RequiredArgsConstructor
@Tag(name = "Rentabilite")
public class RentabiliteController {

    private final RentabiliteService service;

    @GetMapping("/global")
    @Operation(summary = "Rentabilite du parc, repartition par camion et palmares")
    public RentabiliteDto.Global global(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        LocalDate finRetenue = fin == null ? LocalDate.now() : fin;
        return service.global(debut == null ? finRetenue.withDayOfMonth(1) : debut, finRetenue);
    }

    @GetMapping("/camions/{camionId}")
    @Operation(summary = "Fiche performance d'un camion : revenus, depenses, marge, utilisation")
    public RentabiliteDto.FicheCamion camion(
            @PathVariable Long camionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        LocalDate finRetenue = fin == null ? LocalDate.now() : fin;
        return service.ficheCamion(camionId, debut == null ? finRetenue.withDayOfMonth(1) : debut, finRetenue);
    }
}
