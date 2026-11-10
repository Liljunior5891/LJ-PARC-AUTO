package tg.parcauto.api.gps;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.gps.integration.RegistreAdaptateurs;
import tg.parcauto.api.gps.position.PositionGpsService;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;

/**
 * Entree des traceurs et passerelles GPS, sans session d'utilisateur : le
 * jeton GPS de l'entreprise (en-tete X-Jeton-Gps, ou parametre « jeton » pour
 * une passerelle qui ne sait pas poser d'en-tete) designe l'entreprise
 * ({@link ContexteGpsInterceptor}) ; les positions ne touchent que ses vehicules.
 */
@RestController
@RequestMapping("/api/public/gps")
@RequiredArgsConstructor
@ModuleRequis(value = ModuleApplicatif.GESTION_PARC, publique = true)
@Tag(name = "Geolocalisation - traceurs")
public class GpsPublicController {

    private final RegistreAdaptateurs registre;
    private final PositionGpsService positions;

    public record Reception(int enregistrees) {
    }

    @PostMapping("/positions/{fournisseur}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Recoit les positions d'un traceur ou d'une passerelle, avec le jeton GPS de l'entreprise")
    public Reception recevoir(@PathVariable String fournisseur, @RequestBody JsonNode charge) {
        return new Reception(positions.enregistrer(registre.pour(fournisseur).convertir(charge), fournisseur).size());
    }
}
