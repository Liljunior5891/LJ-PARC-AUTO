package tg.parcauto.api.maintenance.incident;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import tg.parcauto.api.security.Autorisations;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
@Tag(name = "Incidents")
public class IncidentController {

    private final IncidentService service;

    @GetMapping
    @Operation(summary = "Historique des incidents")
    public Page<IncidentDto.Reponse> lister(@PageableDefault(size = 20) Pageable pageable) {
        return service.lister(pageable);
    }

    @GetMapping("/ouverts")
    @Operation(summary = "Incidents non resolus")
    public List<IncidentDto.Reponse> ouverts() {
        return service.ouverts();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'un incident")
    public IncidentDto.Reponse detail(@PathVariable Long id) {
        return service.parId(id);
    }

    /** Le chauffeur declare depuis l'application mobile (chapitres 32 et 33). */
    @PostMapping
    @PreAuthorize(Autorisations.TERRAIN)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Declare un incident et notifie le responsable")
    public IncidentDto.Reponse declarer(@Valid @RequestBody IncidentDto.Requete requete) {
        return service.declarer(requete);
    }

    @PatchMapping("/{id}/traitement")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Fait avancer le traitement et libere le camion s'il est resolu")
    public IncidentDto.Reponse traiter(@PathVariable Long id,
                                       @Valid @RequestBody IncidentDto.Traitement traitement) {
        return service.traiter(id, traitement);
    }
}
