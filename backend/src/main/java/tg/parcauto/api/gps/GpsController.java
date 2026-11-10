package tg.parcauto.api.gps;

import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
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
@ModuleRequis(ModuleApplicatif.GESTION_PARC)
@RequestMapping("/api/gps")
@RequiredArgsConstructor
@Tag(name = "Geolocalisation")
public class GpsController {

    private final CarteParcService carteService;
    private final tg.parcauto.api.gps.position.PositionGpsService positionService;
    private final RegistreAdaptateurs registre;
    private final JetonGpsService jetons;

    /**
     * Saisie manuelle de positions par l'administrateur (essais). Les traceurs
     * envoient sur /api/public/gps/positions/{fournisseur} avec le jeton GPS.
     */
    @PostMapping("/positions/{fournisseur}")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Recoit les positions d'un fournisseur de traceurs")
    public int ingerer(@PathVariable String fournisseur, @RequestBody JsonNode charge) {
        var positions = registre.pour(fournisseur).convertir(charge);
        return positionService.enregistrer(positions, fournisseur).size();
    }

    @GetMapping("/jeton")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Etat du jeton GPS de l'entreprise (sans le jeton lui-meme)")
    public JetonGpsService.Etat etatJeton() {
        return jetons.etat();
    }

    public record NouveauJeton(String jeton, String adresse) {
    }

    @PostMapping("/jeton")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Genere (ou remplace) le jeton GPS de l'entreprise ; il n'est montre qu'une fois")
    public NouveauJeton genererJeton() {
        String jeton = jetons.generer();
        return new NouveauJeton(jeton, "/api/public/gps/positions/traccar?jeton=" + jeton);
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
        // Un trajet se lit sur un mois au plus.
        if (debutRetenu.isBefore(finRetenue.minus(java.time.Duration.ofDays(31)))) {
            debutRetenu = finRetenue.minus(java.time.Duration.ofDays(31));
        }
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
