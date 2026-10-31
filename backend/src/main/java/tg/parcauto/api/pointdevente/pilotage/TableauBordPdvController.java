package tg.parcauto.api.pointdevente.pilotage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.Autorisations;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@RequestMapping("/api/pdv/tableau-bord")
@RequiredArgsConstructor
@PreAuthorize(Autorisations.PDV_GESTION)
@Tag(name = "Point de vente - tableau de bord")
public class TableauBordPdvController {

    private final TableauBordPdvService service;

    /** Sans bornes : la journee en cours dans le fuseau indique. */
    @GetMapping
    @Operation(summary = "Indicateurs de vente sur une periode")
    public TableauBordPdvService.TableauBord tableauBord(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fin,
            @RequestParam(defaultValue = "Africa/Lome") String fuseau) {
        ZoneId zone;
        try {
            zone = ZoneId.of(fuseau);
        } catch (DateTimeException e) {
            throw new BusinessException("Fuseau horaire inconnu : " + fuseau);
        }
        Instant debutRetenu = debut == null ? LocalDate.now(zone).atStartOfDay(zone).toInstant() : debut;
        Instant finRetenue = fin == null ? LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant() : fin;
        if (!debutRetenu.isBefore(finRetenue)) {
            throw new BusinessException("La date de debut doit preceder la date de fin");
        }
        return service.calculer(debutRetenu, finRetenue, zone);
    }
}
