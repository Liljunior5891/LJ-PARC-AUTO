package tg.parcauto.api.pilotage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tableau-bord")
@RequiredArgsConstructor
@Tag(name = "Tableau de bord")
public class TableauBordController {

    private final TableauBordService service;

    @GetMapping("/parc")
    @Operation(summary = "Indicateurs du parc : effectifs, statuts, affectations et alertes")
    public TableauBordDto.Parc parc(@RequestParam(defaultValue = "30") int joursAlerte) {
        return service.parc(joursAlerte);
    }

    @GetMapping("/finances")
    @Operation(summary = "Indicateurs financiers : chiffre d'affaires, depenses, resultat, creances")
    public TableauBordDto.Finances finances(
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate debut,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate fin) {
        java.time.LocalDate finRetenue = fin == null ? java.time.LocalDate.now() : fin;
        return service.finances(debut == null ? finRetenue.withDayOfMonth(1) : debut, finRetenue);
    }

    @GetMapping("/vue-parc")
    @Operation(summary = "Vue globale du parc : vehicule, chauffeur, statut, activite et client")
    public List<VueParcDto> vueParc() {
        return service.vueParc();
    }
}
