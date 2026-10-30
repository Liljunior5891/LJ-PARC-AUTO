package tg.parcauto.api.pilotage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pilotage")
@RequiredArgsConstructor
@Tag(name = "Pilotage")
public class PilotageController {

    private final PilotageService service;
    private final RapportService rapportService;
    private final RapportPdfService rapportPdfService;
    private final NotificationService notificationService;
    private final EvolutionService evolutionService;

    @GetMapping("/kpi")
    @Operation(summary = "Indicateurs de direction du chapitre 41")
    public KpiDto kpi(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        LocalDate finRetenue = fin == null ? LocalDate.now() : fin;
        return service.kpi(debut == null ? finRetenue.withDayOfMonth(1) : debut, finRetenue);
    }

    @GetMapping("/camions/{camionId}/synthese")
    @Operation(summary = "Fiche synthetique d'un vehicule : chapitre 36")
    public FicheSyntheseDto synthese(
            @PathVariable Long camionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        LocalDate finRetenue = fin == null ? LocalDate.now() : fin;
        return service.ficheSynthese(camionId,
                debut == null ? finRetenue.withDayOfMonth(1) : debut, finRetenue);
    }

    /** Serie mensuelle recettes / depenses / resultat, pour le tableau de bord. */
    @GetMapping("/evolution")
    @Operation(summary = "Evolution mensuelle des recettes et des depenses")
    public List<EvolutionMensuelleDto> evolution(@RequestParam(defaultValue = "6") int mois) {
        return evolutionService.evolution(mois);
    }

    @GetMapping("/notifications")
    @Operation(summary = "Notifications consolidees de tous les modules")
    public List<NotificationDto> notifications(@RequestParam(defaultValue = "30") int joursAlerte) {
        return notificationService.notifications(joursAlerte);
    }

    @GetMapping("/notifications/synthese")
    @Operation(summary = "Compteurs de notifications par niveau")
    public NotificationDto.Synthese syntheseNotifications(
            @RequestParam(defaultValue = "30") int joursAlerte) {
        return notificationService.synthese(joursAlerte);
    }

    @GetMapping("/rapports")
    @Operation(summary = "Rapports disponibles a l'export")
    public List<Map<String, String>> rapports() {
        return Arrays.stream(RapportService.TypeRapport.values())
                .map(type -> Map.of("valeur", type.name(), "libelle", type.getLibelle()))
                .toList();
    }

    /** Export CSV, ouvrable directement dans Excel ou LibreOffice (chapitre 35). */
    @GetMapping(value = "/rapports/{type}", produces = "text/csv")
    @Operation(summary = "Telecharge un rapport au format CSV")
    public ResponseEntity<byte[]> exporter(
            @PathVariable RapportService.TypeRapport type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        LocalDate finRetenue = fin == null ? LocalDate.now() : fin;
        LocalDate debutRetenu = debut == null ? finRetenue.withDayOfMonth(1) : debut;
        rapportService.verifierPeriode(debutRetenu, finRetenue);

        byte[] contenu = rapportService.produire(type, debutRetenu, finRetenue)
                .getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + type.nomFichier(debutRetenu, finRetenue) + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(contenu);
    }

    /** Catalogue des rapports PDF, pour alimenter l'ecran Rapports. */
    @GetMapping("/rapports-pdf")
    @Operation(summary = "Catalogue des rapports editables en PDF")
    public List<Map<String, Object>> rapportsPdf() {
        return Arrays.stream(TypeRapportPdf.values())
                .map(type -> Map.<String, Object>of(
                        "valeur", type.name(),
                        "libelle", type.getLibelle(),
                        "description", type.getDescription(),
                        "selectionCamions", type.isSelectionCamions()))
                .toList();
    }

    /**
     * Edition PDF d'un rapport (chapitre 35).
     *
     * @param camionIds selection facultative ; sans elle le rapport porte sur tout le parc
     */
    @GetMapping(value = "/rapports-pdf/{type}", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Telecharge un rapport au format PDF")
    public ResponseEntity<byte[]> exporterPdf(
            @PathVariable TypeRapportPdf type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) List<Long> camionIds) {
        LocalDate finRetenue = fin == null ? LocalDate.now() : fin;
        LocalDate debutRetenu = debut == null ? finRetenue.withDayOfMonth(1) : debut;

        byte[] contenu = rapportPdfService.produire(type, debutRetenu, finRetenue,
                camionIds == null ? List.of() : camionIds);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + type.nomFichier(debutRetenu, finRetenue) + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(contenu);
    }
}
