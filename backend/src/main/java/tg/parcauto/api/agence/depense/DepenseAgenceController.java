package tg.parcauto.api.agence.depense;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.time.LocalDate;
import java.util.List;

/** Depenses des agences, categories et charges recurrentes. */
@RestController
@ModuleRequis(ModuleApplicatif.AGENCES)
@RequestMapping("/api/agences/depenses")
@RequiredArgsConstructor
@Tag(name = "Agences - depenses")
public class DepenseAgenceController {

    private final DepenseAgenceService service;

    public record Motif(@NotBlank @Size(max = 500) String motif) {
    }

    public record DemandeGeneration(@jakarta.validation.constraints.NotNull Long agenceId, String mois, Long compteId) {
    }

    @GetMapping("/categories")
    @Operation(summary = "Categories de depense (posees a la premiere lecture)")
    public List<DepenseAgenceService.CategorieDto> categories() {
        return service.categories();
    }

    @PostMapping("/categories")
    @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajoute une categorie")
    public DepenseAgenceService.CategorieDto creerCategorie(@Valid @RequestBody DepenseAgenceService.CategorieRequete r) {
        return service.enregistrerCategorie(null, r);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")
    @Operation(summary = "Modifie une categorie")
    public DepenseAgenceService.CategorieDto modifierCategorie(@PathVariable Long id, @Valid @RequestBody DepenseAgenceService.CategorieRequete r) {
        return service.enregistrerCategorie(id, r);
    }

    @GetMapping
    @PreAuthorize("@droitsAgence.a('DEPENSE_SAISIR') or @droitsAgence.a('DEPENSE_ANNULER')")
    @Operation(summary = "Depenses des agences visibles ; un caissier ne voit que les siennes")
    public Page<DepenseAgenceService.DepenseDto> lister(
            @RequestParam(required = false) Long agenceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) Long categorieId,
            @RequestParam(required = false) DepenseAgence.Statut statut,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 30) Pageable pageable) {
        return service.lister(new DepenseAgenceService.Filtres(agenceId, du, au, categorieId, statut, q), pageable);
    }

    @GetMapping("/synthese")
    @PreAuthorize("@droitsAgence.a('SOLDES_CONSULTER')")
    @Operation(summary = "Suivi mensuel : total, par categorie, comparaison avec les 5 mois precedents")
    public DepenseAgenceService.Synthese synthese(@RequestParam(required = false) Long agenceId, @RequestParam(required = false) String mois) {
        return service.synthese(agenceId, mois);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@droitsAgence.a('DEPENSE_SAISIR') or @droitsAgence.a('DEPENSE_ANNULER')")
    @Operation(summary = "Detail d'une depense")
    public DepenseAgenceService.DepenseDto detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @PreAuthorize("@droitsAgence.a('DEPENSE_SAISIR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistre une depense : le compte choisi (ou la caisse du caissier) est debite")
    public DepenseAgenceService.DepenseDto creer(@Valid @RequestBody DepenseAgenceService.Requete r,
                                                 @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.creer(r, principal);
    }

    @PostMapping("/{id}/annulation")
    @PreAuthorize("@droitsAgence.a('DEPENSE_ANNULER')")
    @Operation(summary = "Annule une depense par l'ecriture inverse, avec motif")
    public DepenseAgenceService.DepenseDto annuler(@PathVariable Long id, @Valid @RequestBody Motif motif,
                                                   @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.annuler(id, motif.motif(), principal);
    }

    @PostMapping(value = "/{id}/justificatif", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@droitsAgence.a('DEPENSE_SAISIR') or @droitsAgence.a('DEPENSE_ANNULER')")
    @Operation(summary = "Joint ou remplace le justificatif (photo ou PDF, 15 Mo au plus)")
    public DepenseAgenceService.DepenseDto joindre(@PathVariable Long id, @RequestPart("fichier") MultipartFile fichier) {
        return service.joindre(id, fichier);
    }

    @GetMapping("/{id}/justificatif")
    @PreAuthorize("@droitsAgence.a('DEPENSE_SAISIR') or @droitsAgence.a('DEPENSE_ANNULER')")
    @Operation(summary = "Telecharge le justificatif")
    public ResponseEntity<Resource> justificatif(@PathVariable Long id) {
        DepenseAgenceService.DepenseDto d = service.detail(id);
        String nom = d.justificatifNom() == null ? "justificatif-" + d.numero() : d.justificatifNom();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nom.replace("\"", "") + "\"")
                .contentType(MediaTypeFactory.getMediaType(nom).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .body(service.justificatif(id));
    }

    @GetMapping("/recurrentes")
    @PreAuthorize("@droitsAgence.a('DEPENSE_SAISIR') or @droitsAgence.a('PARAMETRAGE')")
    @Operation(summary = "Charges recurrentes de l'agence et si elles sont reglees pour le mois")
    public List<DepenseAgenceService.RecurrenteDto> recurrentes(@RequestParam Long agenceId, @RequestParam(required = false) String mois) {
        return service.recurrentes(agenceId, mois);
    }

    @PostMapping("/recurrentes")
    @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajoute une charge recurrente")
    public DepenseAgenceService.RecurrenteDto creerRecurrente(@Valid @RequestBody DepenseAgenceService.RecurrenteRequete r) {
        return service.enregistrerRecurrente(null, r);
    }

    @PutMapping("/recurrentes/{id}")
    @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")
    @Operation(summary = "Modifie une charge recurrente")
    public DepenseAgenceService.RecurrenteDto modifierRecurrente(@PathVariable Long id, @Valid @RequestBody DepenseAgenceService.RecurrenteRequete r) {
        return service.enregistrerRecurrente(id, r);
    }

    @PostMapping("/recurrentes/generation")
    @PreAuthorize("@droitsAgence.a('DEPENSE_SAISIR')")
    @Operation(summary = "Regle les charges recurrentes du mois qui ne le sont pas encore (jamais de doublon)")
    public DepenseAgenceService.Generation generer(@Valid @RequestBody DemandeGeneration d,
                                                   @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.generer(d.agenceId(), d.mois(), d.compteId(), principal);
    }
}
