package tg.parcauto.api.gps.zone;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/gps/zones")
@RequiredArgsConstructor
@Tag(name = "Geofencing")
public class ZoneGeofenceController {

    private static final String GESTION = "hasAnyRole('ADMIN','GESTIONNAIRE_PARC','LOGISTIQUE')";

    private final ZoneGeofenceService service;

    @GetMapping
    @Operation(summary = "Zones surveillees : siege, depots, agences, zones interdites")
    public List<ZoneGeofenceDto.Reponse> lister(@RequestParam(defaultValue = "true") boolean actifSeulement) {
        return service.lister(actifSeulement);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'une zone")
    public ZoneGeofenceDto.Reponse detail(@PathVariable Long id) {
        return service.parId(id);
    }

    @PostMapping
    @PreAuthorize(GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cree une zone surveillee")
    public ZoneGeofenceDto.Reponse creer(@Valid @RequestBody ZoneGeofenceDto.Requete requete) {
        return service.creer(requete);
    }

    @PutMapping("/{id}")
    @PreAuthorize(GESTION)
    @Operation(summary = "Met a jour une zone")
    public ZoneGeofenceDto.Reponse modifier(@PathVariable Long id,
                                            @Valid @RequestBody ZoneGeofenceDto.Requete requete) {
        return service.modifier(id, requete);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(GESTION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desactive une zone")
    public void desactiver(@PathVariable Long id) {
        service.desactiver(id);
    }
}
