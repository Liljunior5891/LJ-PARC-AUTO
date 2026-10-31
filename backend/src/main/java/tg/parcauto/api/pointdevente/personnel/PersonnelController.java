package tg.parcauto.api.pointdevente.personnel;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.Autorisations;

import java.util.List;

@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@RequestMapping("/api/pdv/personnel")
@RequiredArgsConstructor
@Tag(name = "Point de vente - personnel")
public class PersonnelController {

    private final PersonnelService service;

    @GetMapping
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Comptes du point de vente, pour affecter caisses et ecrans")
    public List<PersonnelService.Membre> personnel() {
        return service.personnel();
    }
}
