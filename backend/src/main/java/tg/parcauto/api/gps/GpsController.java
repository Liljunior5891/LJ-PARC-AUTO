package tg.parcauto.api.gps;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.gps.integration.RegistreAdaptateurs;
import tg.parcauto.api.gps.position.PositionGpsDto;

import java.time.Instant;
import java.util.List;
import tg.parcauto.api.security.Autorisations;

@RestController
@RequestMapping("/api/gps")
@RequiredArgsConstructor
@Tag(name = "Geolocalisation")
public class GpsController {

    private final CarteParcService carteService;
    private final tg.parcauto.api.gps.position.PositionGpsService positionService;
    private final RegistreAdaptateurs registre;

    /**
     * Point d'entree des traceurs. Le fournisseur figure dans l'URL : la charge
     * utile est traduite par l'adaptateur correspondant (chapitre 22).
     */
    @PostMapping("/positions/{fournisseur}")
    @PreAuthorize(Autorisations.GESTION)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Recoit les positions d'un fournisseur de traceurs")
    public int ingerer(@PathVariable String fournisseur, @RequestBody JsonNode charge) {
        var positions = registre.pour(fournisseur).convertir(charge);
        return positionService.enregistrer(positions, fournisseur).size();
    }

    @GetMapping("/fournisseurs")
    @Operation(summary = "Fournisseurs de traceurs pris en charge")
    public List<String> fournisseurs() {
        return registre.fournisseurs();
    }

    @GetMapping("/carte")
    @Operation(summary = "Marqueurs de la carte du parc en temps reel")
    public List<PositionGpsDto.MarqueurCarte> carte() {
        return carteService.marqueurs();
    }

    @GetMapping("/camions/{camionId}/trajet")
    @Operation(summary = "Trajet d'un vehicule sur une periode : itineraire, distance, vitesses, arrets")
    public PositionGpsDto.Trajet trajet(
            @PathVariable Long camionId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant debut,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fin) {
        Instant finRetenue = fin == null ? Instant.now() : fin;
        Instant debutRetenu = debut == null ? finRetenue.minusSeconds(86_400) : debut;
        return carteService.trajet(camionId, debutRetenu, finRetenue);
    }

    @GetMapping("/camions/{camionId}/dernieres")
    @Operation(summary = "Dernieres positions connues d'un vehicule")
    public List<PositionGpsDto.Reponse> dernieres(@PathVariable Long camionId,
                                                  @RequestParam(defaultValue = "20") int nombre) {
        return positionService.dernieres(camionId, nombre).stream()
                .map(PositionGpsDto.Reponse::de)
                .toList();
    }
}
