package tg.parcauto.api.finance.rentabilite;

import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
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


// Donnees financieres : gestionnaires et administrateur seulement (pas les chauffeurs).
@org.springframework.security.access.prepost.PreAuthorize(tg.parcauto.api.security.Autorisations.GESTION)
@RestController
@ModuleRequis(ModuleApplicatif.GESTION_PARC)
@RequestMapping("/api/rentabilite")
@RequiredArgsConstructor
@Tag(name = "Rentabilite")
public class RentabiliteController {

    private final RentabiliteService service;

    @GetMapping("/global")
    @Operation(summary = "Rentabilite du parc, repartition par vehicule et palmares")
    public RentabiliteDto.Global global(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        LocalDate finRetenue = fin == null ? LocalDate.now() : fin;
        LocalDate debutRetenu = debut == null ? finRetenue.withDayOfMonth(1) : debut;
        // Periode bornee (5 ans au plus), comme le pilotage : pas de calcul jour par jour sans fin.
        tg.parcauto.api.pilotage.RapportService.exigerPeriode(debutRetenu, finRetenue);
        return service.global(debutRetenu, finRetenue);
    }

    @GetMapping("/camions/{camionId}")
    @Operation(summary = "Fiche performance d'un vehicule : revenus, depenses, marge, utilisation")
    public RentabiliteDto.FicheCamion camion(
            @PathVariable Long camionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        LocalDate finRetenue = fin == null ? LocalDate.now() : fin;
        LocalDate debutRetenu = debut == null ? finRetenue.withDayOfMonth(1) : debut;
        tg.parcauto.api.pilotage.RapportService.exigerPeriode(debutRetenu, finRetenue);
        return service.ficheCamion(camionId, debutRetenu, finRetenue);
    }
}
