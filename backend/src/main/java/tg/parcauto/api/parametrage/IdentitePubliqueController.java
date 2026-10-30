package tg.parcauto.api.parametrage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Identite visible avant toute authentification.
 * <p>
 * La page de connexion et le site vitrine doivent afficher le logo, le nom et
 * les coordonnees de l'entreprise alors que personne n'est encore connecte :
 * ces points d'entree, et eux seuls, sont ouverts. Ils ne divulguent que ce qui
 * figure deja sur les documents remis aux clients ; ni les references fiscales
 * ni les coordonnees bancaires n'y transitent.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "Parametrage")
public class IdentitePubliqueController {

    private final SocieteService service;

    @GetMapping("/societe")
    @Operation(summary = "Nom et logo de l'entreprise, pour la page de connexion")
    public SocieteDto.Identite identite() {
        return service.identite();
    }

    @GetMapping("/vitrine")
    @Operation(summary = "Identite et coordonnees de l'entreprise, pour le site vitrine")
    public SocieteDto.Vitrine vitrine() {
        return service.vitrine();
    }
}
