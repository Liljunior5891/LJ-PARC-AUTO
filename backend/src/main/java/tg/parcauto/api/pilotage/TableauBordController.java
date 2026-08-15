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

    @GetMapping("/exploitation")
    @Operation(summary = "Indicateurs d'exploitation : contrats, missions, livraisons")
    public TableauBordDto.Exploitation exploitation(@RequestParam(defaultValue = "15") int joursEcheance) {
        return service.exploitation(joursEcheance);
    }

    @GetMapping("/vue-parc")
    @Operation(summary = "Vue globale du parc : camion, chauffeur, statut, activite et client")
    public List<VueParcDto> vueParc() {
        return service.vueParc();
    }
}
