package tg.parcauto.api.agence.mouvement;

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
import java.util.Map;

/** Mouvements entre comptes des agences et regularisations. */
@RestController
@ModuleRequis(ModuleApplicatif.AGENCES)
@RequestMapping("/api/agences/mouvements")
@RequiredArgsConstructor
@Tag(name = "Agences - mouvements entre comptes")
public class MouvementController {

    private final MouvementService service;

    public record Motif(@NotBlank @Size(max = 500) String motif) {
    }

    @GetMapping
    @PreAuthorize("@droitsAgence.a('MOUVEMENT_SAISIR') or @droitsAgence.a('MOUVEMENT_VALIDER')")
    @Operation(summary = "Mouvements des agences visibles, du plus recent au plus ancien")
    public Page<MouvementService.MouvementDto> lister(
            @RequestParam(required = false) Long agenceId,
            @RequestParam(required = false) MouvementAgence.Statut statut,
            @RequestParam(required = false) MouvementAgence.Type type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @PageableDefault(size = 30) Pageable pageable) {
        return service.lister(new MouvementService.Filtres(agenceId, statut, type, du, au), pageable);
    }

    @GetMapping("/en-attente")
    @Operation(summary = "Nombre de mouvements en attente de validation")
    public Map<String, Long> enAttente() {
        return Map.of("nombre", service.enAttente());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@droitsAgence.a('MOUVEMENT_SAISIR') or @droitsAgence.a('MOUVEMENT_VALIDER')")
    @Operation(summary = "Detail d'un mouvement")
    public MouvementService.MouvementDto detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @PreAuthorize("@droitsAgence.a('MOUVEMENT_SAISIR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Saisit un mouvement ; au-dela du seuil, ou pour une regularisation, il attend une validation")
    public MouvementService.MouvementDto creer(@Valid @RequestBody MouvementService.Requete requete,
                                               @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.creer(requete, principal);
    }

    @PostMapping("/{id}/validation")
    @PreAuthorize("@droitsAgence.a('MOUVEMENT_VALIDER')")
    @Operation(summary = "Valide un mouvement en attente (par une autre personne que son auteur)")
    public MouvementService.MouvementDto valider(@PathVariable Long id, @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.valider(id, principal);
    }

    @PostMapping("/{id}/rejet")
    @PreAuthorize("@droitsAgence.a('MOUVEMENT_VALIDER')")
    @Operation(summary = "Rejette un mouvement en attente, avec motif")
    public MouvementService.MouvementDto rejeter(@PathVariable Long id, @Valid @RequestBody Motif motif,
                                                 @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.rejeter(id, motif.motif(), principal);
    }

    @PostMapping("/{id}/annulation")
    @PreAuthorize("@droitsAgence.a('MOUVEMENT_VALIDER')")
    @Operation(summary = "Annule un mouvement valide par l'ecriture inverse")
    public MouvementService.MouvementDto annuler(@PathVariable Long id, @Valid @RequestBody Motif motif,
                                                 @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.annuler(id, motif.motif(), principal);
    }

    @PostMapping(value = "/{id}/piece", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@droitsAgence.a('MOUVEMENT_SAISIR') or @droitsAgence.a('MOUVEMENT_VALIDER')")
    @Operation(summary = "Joint ou remplace la piece justificative (photo ou PDF, 15 Mo au plus)")
    public MouvementService.MouvementDto joindre(@PathVariable Long id, @RequestPart("fichier") MultipartFile fichier) {
        return service.joindre(id, fichier);
    }

    @GetMapping("/{id}/piece")
    @PreAuthorize("@droitsAgence.a('MOUVEMENT_SAISIR') or @droitsAgence.a('MOUVEMENT_VALIDER')")
    @Operation(summary = "Telecharge la piece justificative")
    public ResponseEntity<Resource> piece(@PathVariable Long id) {
        MouvementService.MouvementDto m = service.detail(id);
        Resource fichier = service.piece(id);
        String nom = m.pieceNom() == null ? "piece-" + m.reference() : m.pieceNom();
        return tg.parcauto.api.common.FichierServi.reponse(fichier, nom, true);
    }
}
