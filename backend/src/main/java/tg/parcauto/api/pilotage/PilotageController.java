package tg.parcauto.api.pilotage;

import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
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
@ModuleRequis(ModuleApplicatif.GESTION_PARC)
// Chiffres, marges, clients et creances : la direction et la gestion, jamais le chauffeur.
@org.springframework.security.access.prepost.PreAuthorize(tg.parcauto.api.security.Autorisations.GESTION)
@RequestMapping("/api/pilotage")
@RequiredArgsConstructor
@Tag(name = "Pilotage")
public class PilotageController {

    private final PilotageService service;
    private final RapportService rapportService;
    private final RapportPdfService rapportPdfService;
    private final NotificationService notificationService;
    private final EvolutionService evolutionService;
    private final tg.parcauto.api.common.LimiteurRequetes limiteur;

    @GetMapping("/kpi")
    @Operation(summary = "Indicateurs de direction du chapitre 41")
    public KpiDto kpi(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        LocalDate finRetenue = fin == null ? LocalDate.now() : fin;
        LocalDate debutRetenu = debut == null ? finRetenue.withDayOfMonth(1) : debut;
        rapportService.verifierPeriode(debutRetenu, finRetenue);
        return service.kpi(debutRetenu, finRetenue);
    }

    @GetMapping("/camions/{camionId}/synthese")
    @Operation(summary = "Fiche synthetique d'un vehicule : chapitre 36")
    public FicheSyntheseDto synthese(
            @PathVariable Long camionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        LocalDate finRetenue = fin == null ? LocalDate.now() : fin;
        LocalDate debutRetenu = debut == null ? finRetenue.withDayOfMonth(1) : debut;
        rapportService.verifierPeriode(debutRetenu, finRetenue);
        return service.ficheSynthese(camionId, debutRetenu, finRetenue);
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
        return notificationService.notifications(RapportService.joursAlerte(joursAlerte));
    }

    @GetMapping("/notifications/synthese")
    @Operation(summary = "Compteurs de notifications par niveau")
    public NotificationDto.Synthese syntheseNotifications(
            @RequestParam(defaultValue = "30") int joursAlerte) {
        return notificationService.synthese(RapportService.joursAlerte(joursAlerte));
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
        rapportService.verifierPeriode(debutRetenu, finRetenue);

        // Selection dedoublonnee et plafonnee : chaque vehicule coute plusieurs dizaines de requetes.
        List<Long> selection = camionIds == null ? List.of() : camionIds.stream().distinct().toList();
        if (selection.size() > CAMIONS_PDF_MAX) {
            throw new tg.parcauto.api.common.BusinessException("Choisissez au plus " + CAMIONS_PDF_MAX
                    + " vehicules, ou aucun pour tout le parc");
        }
        // Quelques exports par minute et par utilisateur : un PDF mobilise la base.
        limiteur.exiger("rapport-pdf:" + utilisateurCourant(), 10, java.time.Duration.ofMinutes(1),
                "Trop d'exports a la suite : patientez une minute");
        byte[] contenu = rapportPdfService.produire(type, debutRetenu, finRetenue, selection);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + type.nomFichier(debutRetenu, finRetenue) + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(contenu);
    }

    private static final int CAMIONS_PDF_MAX = 50;

    private static String utilisateurCourant() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "anonyme" : auth.getName();
    }
}
