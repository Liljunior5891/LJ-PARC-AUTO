package tg.parcauto.api.pointdevente.rapport;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.parametrage.SocieteService;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.Autorisations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Rapports du point de vente, leurs exports CSV, Excel et PDF, et l'import des produits et du stock. */
@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@RequestMapping("/api/pdv/rapports")
@PreAuthorize(Autorisations.PDV_GESTION)
@RequiredArgsConstructor
@Tag(name = "Point de vente - rapports")
public class RapportController {

    private static final MediaType EXCEL =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    /** Un fichier importe ne depasse pas cette taille. */
    private static final long TAILLE_MAX = 5L * 1024 * 1024;

    private final RapportPdvService service;
    private final ImportService importService;
    private final SocieteService societeService;

    @GetMapping
    @Operation(summary = "Rapports disponibles pour l'etablissement")
    public List<RapportPdvService.Catalogue> catalogue() {
        return service.catalogue();
    }

    @GetMapping("/{type}")
    @Operation(summary = "Calcule un rapport sur une periode")
    public Rapport rapport(@PathVariable TypeRapport type,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant debut,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fin,
                           @RequestParam(required = false) Long article,
                           @RequestParam(required = false) Long ecran,
                           @RequestParam(defaultValue = "Africa/Lome") String fuseau) {
        ZoneId zone = zone(fuseau);
        Instant[] bornes = bornes(debut, fin, zone);
        return service.calculer(type, bornes[0], bornes[1], zone, article, ecran);
    }

    @GetMapping("/{type}/export")
    @Operation(summary = "Exporte un rapport en CSV, en Excel ou en PDF")
    public ResponseEntity<byte[]> exporter(@PathVariable TypeRapport type,
                                           @RequestParam(defaultValue = "xlsx") String format,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant debut,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fin,
                                           @RequestParam(required = false) Long article,
                                           @RequestParam(required = false) Long ecran,
                                           @RequestParam(defaultValue = "Africa/Lome") String fuseau) {
        ZoneId zone = zone(fuseau);
        Instant[] bornes = bornes(debut, fin, zone);
        Rapport rapport = service.calculer(type, bornes[0], bornes[1], zone, article, ecran);
        String nom = type.name().toLowerCase().replace('_', '-')
                + (article != null && type.isParArticle() ? "-" + article : "")
                + (ecran != null && type.isParEcran() ? "-ecran-" + ecran : "") + "-" + LocalDate.now(zone);
        return fichier(rapport, format, nom, zone);
    }

    @GetMapping("/import/{type}/modele")
    @Operation(summary = "Modele d'import pre-rempli avec les donnees actuelles")
    public ResponseEntity<byte[]> modele(@PathVariable ImportService.TypeImport type,
                                         @RequestParam(defaultValue = "xlsx") String format,
                                         @RequestParam(defaultValue = "Africa/Lome") String fuseau) {
        ZoneId zone = zone(fuseau);
        return fichier(importService.modele(type), format, "modele-import-" + type.name().toLowerCase(), zone);
    }

    @PostMapping(value = "/import/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importe des produits ou des quantites de stock (CSV ou Excel) ; en simulation, rien n'est enregistre")
    public ImportService.Bilan importer(@PathVariable ImportService.TypeImport type,
                                        @RequestPart("fichier") MultipartFile fichier,
                                        @RequestParam(defaultValue = "true") boolean simulation) throws IOException {
        if (fichier.isEmpty()) {
            throw new BusinessException("Aucun fichier recu");
        }
        if (fichier.getSize() > TAILLE_MAX) {
            throw new BusinessException("Fichier trop volumineux : 5 Mo au plus");
        }
        List<LecteurTableau.Ligne> lignes = LecteurTableau.lire(fichier.getOriginalFilename(), fichier.getInputStream());
        return importService.importer(type, lignes, simulation);
    }

    private ResponseEntity<byte[]> fichier(Rapport rapport, String format, String nom, ZoneId zone) {
        String extension = switch (format.toLowerCase()) {
            case "csv" -> "csv";
            case "pdf" -> "pdf";
            default -> "xlsx";
        };
        byte[] contenu = switch (extension) {
            case "csv" -> ExportRapport.csv(rapport, zone);
            case "pdf" -> ExportRapport.pdf(rapport, zone, societeService.entite().getNom());
            default -> ExportRapport.excel(rapport, zone);
        };
        MediaType type = switch (extension) {
            case "csv" -> new MediaType("text", "csv", StandardCharsets.UTF_8);
            case "pdf" -> MediaType.APPLICATION_PDF;
            default -> EXCEL;
        };
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(nom + "." + extension).build().toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .body(contenu);
    }

    /** Sans bornes : le mois en cours. */
    private static Instant[] bornes(Instant debut, Instant fin, ZoneId zone) {
        LocalDate aujourdhui = LocalDate.now(zone);
        Instant d = debut != null ? debut : aujourdhui.withDayOfMonth(1).atStartOfDay(zone).toInstant();
        Instant f = fin != null ? fin : aujourdhui.plusDays(1).atStartOfDay(zone).toInstant();
        if (!d.isBefore(f)) {
            throw new BusinessException("La date de debut doit preceder la date de fin");
        }
        return new Instant[]{d, f};
    }

    private static ZoneId zone(String fuseau) {
        try {
            return ZoneId.of(fuseau);
        } catch (DateTimeException e) {
            throw new BusinessException("Fuseau horaire inconnu : " + fuseau);
        }
    }
}
