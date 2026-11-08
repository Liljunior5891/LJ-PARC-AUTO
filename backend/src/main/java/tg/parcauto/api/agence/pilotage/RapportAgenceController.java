package tg.parcauto.api.agence.pilotage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.agence.AuditAgenceService;
import tg.parcauto.api.agence.tresorerie.JourneeCaisse;
import tg.parcauto.api.parametrage.SocieteService;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.pointdevente.rapport.ExportRapport;
import tg.parcauto.api.pointdevente.rapport.Rapport;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** Rapports des agences (ecran et exports Excel, PDF, CSV) et alertes. */
@RestController
@ModuleRequis(ModuleApplicatif.AGENCES)
@RequestMapping("/api/agences")
@RequiredArgsConstructor
@Tag(name = "Agences - rapports et alertes")
public class RapportAgenceController {

    private static final MediaType EXCEL = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final RapportAgenceService rapports;
    private final AlerteAgenceService alertes;
    private final SocieteService societe;
    private final AuditAgenceService audit;

    @GetMapping("/rapports")
    @Operation(summary = "Rapports disponibles et ceux que le profil peut ouvrir")
    public List<RapportAgenceService.Catalogue> catalogue() {
        return rapports.catalogue();
    }

    @GetMapping("/rapports/{type}")
    @Operation(summary = "Rapport sur une periode de journees de caisse (le mois en cours par defaut)")
    public Rapport rapport(@PathVariable RapportAgenceService.Type type, @RequestParam(required = false) Long agenceId,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        LocalDate[] p = periode(du, au);
        return rapports.rapport(type, agenceId, p[0], p[1]);
    }

    @GetMapping("/rapports/{type}/export")
    @org.springframework.transaction.annotation.Transactional
    @Operation(summary = "Export du rapport en Excel (xlsx), PDF ou CSV, avec les memes droits que l'ecran")
    public ResponseEntity<byte[]> exporter(@PathVariable RapportAgenceService.Type type, @RequestParam(required = false) Long agenceId,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                                           @RequestParam(defaultValue = "xlsx") String format) {
        LocalDate[] p = periode(du, au);
        Rapport r = rapports.rapport(type, agenceId, p[0], p[1]);
        String extension = switch (format.toLowerCase()) {
            case "csv" -> "csv";
            case "pdf" -> "pdf";
            default -> "xlsx";
        };
        byte[] contenu = switch (extension) {
            case "csv" -> ExportRapport.csv(r, JourneeCaisse.FUSEAU);
            case "pdf" -> ExportRapport.pdf(r, JourneeCaisse.FUSEAU, societe.entite().getNom());
            default -> ExportRapport.excel(r, JourneeCaisse.FUSEAU);
        };
        MediaType mediaType = switch (extension) {
            case "csv" -> new MediaType("text", "csv", StandardCharsets.UTF_8);
            case "pdf" -> MediaType.APPLICATION_PDF;
            default -> EXCEL;
        };
        audit.tracer(agenceId, "EXPORT", "RAPPORT", null, "Export " + extension.toUpperCase() + " du rapport " + type.getTitre()
                + " (" + p[0] + " au " + p[1] + ")", null, null);
        String nom = "agences-" + type.name().toLowerCase().replace('_', '-') + "-" + p[0] + "-" + p[1] + "." + extension;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nom).build().toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .body(contenu);
    }

    @GetMapping("/alertes")
    @Operation(summary = "Alertes des agences visibles : seuils, ecarts, doublons, stock, echeances, clotures")
    public List<AlerteAgenceService.Alerte> alertes(@RequestParam(required = false) Long agenceId) {
        return alertes.alertes(agenceId);
    }

    private static LocalDate[] periode(LocalDate du, LocalDate au) {
        LocalDate aujourdhui = LocalDate.now(JourneeCaisse.FUSEAU);
        LocalDate fin = au != null ? au : aujourdhui;
        LocalDate debut = du != null ? du : fin.withDayOfMonth(1);
        return debut.isAfter(fin) ? new LocalDate[]{fin, debut} : new LocalDate[]{debut, fin};
    }
}
