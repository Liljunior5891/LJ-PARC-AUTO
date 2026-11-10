package tg.parcauto.api.finance.depense;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.Autorisations;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Depenses du parc : depenses des vehicules et charges generales. Saisie par
 * l'administrateur ou le gestionnaire ; validation et refus par l'administrateur.
 */
// Donnees financieres : gestionnaires et administrateur seulement (pas les chauffeurs).
@org.springframework.security.access.prepost.PreAuthorize(tg.parcauto.api.security.Autorisations.GESTION)
@RestController
@ModuleRequis(ModuleApplicatif.GESTION_PARC)
@RequestMapping("/api/depenses")
@RequiredArgsConstructor
@Tag(name = "Depenses")
public class DepenseController {

    private final DepenseService service;

    @GetMapping
    @Operation(summary = "Liste paginee des depenses ; type VEHICULE ou GENERALE, statut (tous par defaut)")
    public Page<DepenseDto.Reponse> lister(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CategorieDepense categorie,
            @RequestParam(required = false) SousCategorieDepense sousCategorie,
            @RequestParam(required = false) Long camionId,
            @RequestParam(required = false) Long chauffeurId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) StatutDepense statut,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long categorieChargeId,
            @PageableDefault(size = 20, sort = "dateDepense", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.rechercher(q, categorie, sousCategorie, camionId, chauffeurId, debut, fin, statut, type,
                categorieChargeId, pageable);
    }

    @GetMapping("/natures")
    @Operation(summary = "Natures des depenses de vehicule, groupees par categorie")
    public Map<CategorieDepense, List<Map<String, String>>> natures() {
        return Arrays.stream(CategorieDepense.values())
                .filter(categorie -> categorie != CategorieDepense.CHARGE_GENERALE)
                .collect(java.util.stream.Collectors.toMap(
                        categorie -> categorie,
                        categorie -> SousCategorieDepense.de(categorie).stream()
                                .map(nature -> Map.of("valeur", nature.name(), "libelle", nature.getLibelle()))
                                .toList(),
                        (a, b) -> a,
                        () -> new java.util.EnumMap<>(CategorieDepense.class)));
    }

    @GetMapping("/repartition")
    @Operation(summary = "Depenses validees par categorie sur une periode (chapitre 17)")
    public List<DepenseDto.LigneCategorie> repartition(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) Long camionId) {
        return service.repartitionParCategorie(debut, fin, camionId);
    }

    @GetMapping("/repartition/natures")
    @Operation(summary = "Depenses d'un vehicule par nature")
    public List<DepenseDto.LigneNature> repartitionNatures(
            @RequestParam Long camionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return service.repartitionParNature(camionId, debut, fin);
    }

    @GetMapping("/repartition/charges")
    @Operation(summary = "Charges generales validees par categorie sur une periode")
    public List<DepenseDto.LigneCharge> repartitionCharges(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return service.repartitionCharges(debut, fin);
    }

    @GetMapping("/a-valider")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Depenses qui attendent l'approbation de l'administrateur")
    public List<DepenseDto.Reponse> aValider() {
        return service.aValider();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'une depense")
    public DepenseDto.Reponse detail(@PathVariable Long id) {
        return service.parId(id);
    }

    @PostMapping
    @PreAuthorize(Autorisations.GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistre une depense de vehicule ou une charge generale")
    public DepenseDto.Reponse creer(@Valid @RequestBody DepenseDto.Requete requete) {
        return service.creer(requete);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Corrige une depense (repart chez l'administrateur si la validation est activee)")
    public DepenseDto.Reponse modifier(@PathVariable Long id, @Valid @RequestBody DepenseDto.Requete requete) {
        return service.modifier(id, requete);
    }

    @PostMapping("/{id}/annulation")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Annule une depense avec un motif : elle reste visible mais ne compte plus")
    public DepenseDto.Reponse annuler(@PathVariable Long id, @Valid @RequestBody DepenseDto.Motif motif) {
        return service.annuler(id, motif.motif());
    }

    @PostMapping("/{id}/validation")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Approuve une depense en attente : elle compte dans les resultats")
    public DepenseDto.Reponse valider(@PathVariable Long id) {
        return service.valider(id);
    }

    @PostMapping("/{id}/refus")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Refuse une depense en attente, avec un motif")
    public DepenseDto.Reponse refuser(@PathVariable Long id, @Valid @RequestBody DepenseDto.Motif motif) {
        return service.refuser(id, motif.motif());
    }

    @PostMapping(value = "/{id}/justificatif", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Joint un justificatif (photo ou PDF)")
    public DepenseDto.Reponse joindre(@PathVariable Long id, @RequestPart("fichier") MultipartFile fichier) {
        return service.joindre(id, fichier);
    }

    @GetMapping("/{id}/justificatif")
    @Operation(summary = "Telecharge le justificatif")
    public ResponseEntity<Resource> justificatif(@PathVariable Long id) {
        String nom = service.nomJustificatif(id);
        return tg.parcauto.api.common.FichierServi.reponse(service.justificatif(id), nom == null ? "justificatif" : nom, true);
    }

    @DeleteMapping("/{id}/justificatif")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Retire le justificatif")
    public DepenseDto.Reponse retirerJustificatif(@PathVariable Long id) {
        return service.retirerJustificatif(id);
    }
}
