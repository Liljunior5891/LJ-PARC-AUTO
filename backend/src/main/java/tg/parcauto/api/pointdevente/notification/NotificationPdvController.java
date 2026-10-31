package tg.parcauto.api.pointdevente.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.Autorisations;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.util.List;

/** Evenements en attente pour le poste de l'utilisateur, interroges regulierement par le navigateur. */
@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@RequestMapping("/api/pdv/notifications")
@RequiredArgsConstructor
@Tag(name = "Point de vente - notifications")
public class NotificationPdvController {

    private final NotificationPdvService service;

    @GetMapping
    @PreAuthorize(Autorisations.PDV_PERSONNEL)
    @Operation(summary = "Commandes a valider, appels, plats prets, nouveaux bons et caisses ouvertes")
    public List<NotificationPdvService.Evenement> evenements(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.pour(principal);
    }
}
