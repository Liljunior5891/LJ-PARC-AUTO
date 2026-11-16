package tg.parcauto.api.pointdevente.reserve;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.Autorisations;
import tg.parcauto.api.security.UtilisateurPrincipal;

/** Fonds de reserve : lu par l'administrateur et le gerant, qui peuvent y imputer des depenses. */
@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@RequestMapping("/api/pdv/fonds-reserve")
@PreAuthorize(Autorisations.PDV_GESTION)
@RequiredArgsConstructor
@Tag(name = "Point de vente - fonds de reserve")
public class FondsReserveController {

    private final FondsReserveService service;

    @GetMapping
    @Operation(summary = "Solde, apports et depenses du fonds de reserve")
    public FondsReserveService.Etat etat() {
        return service.etat();
    }

    @PostMapping("/apports")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Apport direct de l'entreprise au fonds, depuis le compte de l'entreprise")
    public FondsReserveService.Apport apportDirect(@Valid @RequestBody FondsReserveService.ApportDirect demande,
                                                   @AuthenticationPrincipal UtilisateurPrincipal admin) {
        return service.apportDirect(demande, admin);
    }
}
