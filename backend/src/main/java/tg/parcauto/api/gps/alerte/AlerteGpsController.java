package tg.parcauto.api.gps.alerte;

import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import tg.parcauto.api.security.Autorisations;

@RestController
@ModuleRequis(ModuleApplicatif.GESTION_PARC)
@RequestMapping("/api/gps/alertes")
@RequiredArgsConstructor
@Tag(name = "Alertes GPS")
public class AlerteGpsController {

    private final AlerteGpsService service;

    @GetMapping
    @Operation(summary = "Alertes GPS, filtrables par etat de traitement")
    public Page<AlerteGpsDto.Reponse> lister(@RequestParam(required = false) Boolean traitee,
                                             @PageableDefault(size = 20) Pageable pageable) {
        return service.rechercher(traitee, pageable);
    }

    @GetMapping("/recentes")
    @Operation(summary = "Dernieres alertes non traitees")
    public List<AlerteGpsDto.Reponse> recentes(@RequestParam(defaultValue = "10") int nombre) {
        return service.dernieresNonTraitees(nombre);
    }

    @GetMapping("/synthese")
    @Operation(summary = "Compteurs d'alertes non traitees par type")
    public AlerteGpsDto.Synthese synthese() {
        return service.synthese();
    }

    @PatchMapping("/{id}/traitement")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Marque une alerte comme traitee")
    public AlerteGpsDto.Reponse traiter(@PathVariable Long id) {
        return service.traiter(id);
    }
}
