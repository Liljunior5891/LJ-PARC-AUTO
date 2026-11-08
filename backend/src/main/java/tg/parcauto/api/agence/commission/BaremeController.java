package tg.parcauto.api.agence.commission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

import java.time.LocalDate;
import java.util.List;

/** Baremes de commission des agences et simulation d'une commission. */
@RestController
@ModuleRequis(ModuleApplicatif.AGENCES)
@RequestMapping("/api/agences/baremes")
@RequiredArgsConstructor
@Tag(name = "Agences - baremes de commission")
public class BaremeController {

    private final BaremeService service;

    @GetMapping
    @Operation(summary = "Baremes en cours et a venir ; historique=true ajoute les versions terminees")
    public List<BaremeService.BaremeDto> lister(@RequestParam(required = false) Long serviceId,
                                                @RequestParam(defaultValue = "false") boolean historique) {
        return service.lister(serviceId, historique);
    }

    @GetMapping("/{id}/historique")
    @Operation(summary = "Versions successives d'un bareme")
    public List<BaremeService.BaremeDto> historique(@PathVariable Long id) {
        return service.historique(id);
    }

    @GetMapping("/simulation")
    @Operation(summary = "Commission qu'une operation recevrait, avec le bareme applique")
    public BaremeService.Commission simuler(
            @RequestParam Long serviceId,
            @RequestParam(required = false) Long typeOperationId,
            @RequestParam(required = false) Long reseauId,
            @RequestParam long montant,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour) {
        return service.commission(serviceId, typeOperationId, reseauId, montant, jour);
    }

    @GetMapping("/controle")
    @Operation(summary = "Exemples du cahier des charges compares aux baremes actuels")
    public List<BaremeService.Controle> controle() {
        return service.controle();
    }

    @PostMapping
    @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cree un bareme")
    public BaremeService.BaremeDto creer(@Valid @RequestBody BaremeService.Requete requete) {
        return service.creer(requete);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")
    @Operation(summary = "Modifie un bareme : nouvelle version a partir de la date d'effet s'il est deja en vigueur")
    public BaremeService.BaremeDto modifier(@PathVariable Long id, @Valid @RequestBody BaremeService.Requete requete) {
        return service.modifier(id, requete);
    }

    @PostMapping("/{id}/fin")
    @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")
    @Operation(summary = "Arrete un bareme a une date (aujourd'hui par defaut)")
    public BaremeService.BaremeDto terminer(@PathVariable Long id,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return service.terminer(id, dateFin);
    }

    @PostMapping("/exemples")
    @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")
    @Operation(summary = "Charge les baremes d'exemple du cahier des charges (marques « exemple »)")
    public List<BaremeService.BaremeDto> exemples() {
        return service.chargerExemples();
    }
}
