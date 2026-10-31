package tg.parcauto.api.maintenance.entretien;

import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import tg.parcauto.api.security.Autorisations;

@RestController
@ModuleRequis(ModuleApplicatif.GESTION_PARC)
@RequestMapping("/api/entretiens")
@RequiredArgsConstructor
@Tag(name = "Maintenance")
public class EntretienController {


    private final EntretienService service;

    @GetMapping
    @Operation(summary = "Interventions planifiees ou en cours")
    public List<EntretienDto.Reponse> ouverts() {
        return service.ouverts();
    }

    @GetMapping("/alertes")
    @Operation(summary = "Interventions dont l'echeance est atteinte ou proche")
    public List<EntretienDto.Reponse> alertes() {
        return service.alertes();
    }

    @GetMapping("/camions/{camionId}")
    @Operation(summary = "Historique d'entretien d'un vehicule")
    public List<EntretienDto.Reponse> historique(@PathVariable Long camionId) {
        return service.historiqueDuCamion(camionId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'une intervention")
    public EntretienDto.Reponse detail(@PathVariable Long id) {
        return service.parId(id);
    }

    @PostMapping
    @PreAuthorize(Autorisations.GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Planifie une intervention")
    public EntretienDto.Reponse planifier(@Valid @RequestBody EntretienDto.Requete requete) {
        return service.planifier(requete);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Met a jour une intervention non close")
    public EntretienDto.Reponse modifier(@PathVariable Long id,
                                         @Valid @RequestBody EntretienDto.Requete requete) {
        return service.modifier(id, requete);
    }

    @PatchMapping("/{id}/demarrage")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Passe le vehicule a l'atelier")
    public EntretienDto.Reponse demarrer(@PathVariable Long id) {
        return service.demarrer(id);
    }

    @PatchMapping("/{id}/realisation")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Cloture l'intervention, enregistre la depense et planifie la suivante")
    public EntretienDto.Reponse realiser(@PathVariable Long id,
                                         @Valid @RequestBody EntretienDto.Realisation realisation) {
        return service.realiser(id, realisation);
    }

    @PatchMapping("/{id}/annulation")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Annule l'intervention")
    public EntretienDto.Reponse annuler(@PathVariable Long id,
                                        @RequestParam(required = false) String motif) {
        return service.annuler(id, motif);
    }
}
