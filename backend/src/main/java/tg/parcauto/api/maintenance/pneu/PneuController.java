package tg.parcauto.api.maintenance.pneu;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import tg.parcauto.api.security.Autorisations;

@RestController
@RequestMapping("/api/pneus")
@RequiredArgsConstructor
@Tag(name = "Pneumatiques")
public class PneuController {


    private final PneuService service;

    @GetMapping
    @Operation(summary = "Ensemble des pneus, du stock au rebut")
    public List<PneuDto.Reponse> lister() {
        return service.lister();
    }

    @GetMapping("/alertes")
    @Operation(summary = "Pneus montes arrivant en fin de vie")
    public List<PneuDto.Reponse> aRemplacer() {
        return service.aRemplacer();
    }

    @GetMapping("/camions/{camionId}")
    @Operation(summary = "Pneus d'un vehicule, par position")
    public List<PneuDto.Reponse> duCamion(@PathVariable Long camionId) {
        return service.duCamion(camionId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'un pneu")
    public PneuDto.Reponse detail(@PathVariable Long id) {
        return service.parId(id);
    }

    @PostMapping
    @PreAuthorize(Autorisations.GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistre un pneu en stock")
    public PneuDto.Reponse creer(@Valid @RequestBody PneuDto.Requete requete) {
        return service.creer(requete);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Met a jour la fiche d'un pneu")
    public PneuDto.Reponse modifier(@PathVariable Long id, @Valid @RequestBody PneuDto.Requete requete) {
        return service.modifier(id, requete);
    }

    @PatchMapping("/{id}/montage")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Monte le pneu sur un vehicule, a une position libre")
    public PneuDto.Reponse monter(@PathVariable Long id, @Valid @RequestBody PneuDto.Montage montage) {
        return service.monter(id, montage);
    }

    @PatchMapping("/{id}/depose")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Depose le pneu : retour en stock ou rebut")
    public PneuDto.Reponse deposer(@PathVariable Long id, @Valid @RequestBody PneuDto.Depose depose) {
        return service.deposer(id, depose);
    }
}
