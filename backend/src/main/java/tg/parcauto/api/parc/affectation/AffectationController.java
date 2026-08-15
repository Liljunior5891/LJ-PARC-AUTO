package tg.parcauto.api.parc.affectation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/affectations")
@RequiredArgsConstructor
@Tag(name = "Affectations chauffeur / camion")
public class AffectationController {

    private static final String GESTION = "hasAnyRole('ADMIN','GESTIONNAIRE_PARC','LOGISTIQUE')";

    private final AffectationService service;

    @GetMapping
    @Operation(summary = "Historique des affectations, filtrable par camion, chauffeur ou statut")
    public Page<AffectationDto.Reponse> lister(@RequestParam(required = false) Long camionId,
                                               @RequestParam(required = false) Long chauffeurId,
                                               @RequestParam(required = false) Boolean enCours,
                                               @PageableDefault(size = 20, sort = "dateDebut",
                                                       direction = Sort.Direction.DESC) Pageable pageable) {
        return service.rechercher(camionId, chauffeurId, enCours, pageable);
    }

    @GetMapping("/en-cours")
    @Operation(summary = "Toutes les affectations actuellement ouvertes")
    public List<AffectationDto.Reponse> enCours() {
        return service.enCours();
    }

    @GetMapping("/camion/{camionId}/courante")
    @Operation(summary = "Affectation en cours d'un camion")
    public ResponseEntity<AffectationDto.Reponse> couranteDuCamion(@PathVariable Long camionId) {
        return ResponseEntity.of(service.couranteDuCamion(camionId));
    }

    @GetMapping("/chauffeur/{chauffeurId}/courante")
    @Operation(summary = "Affectation en cours d'un chauffeur")
    public ResponseEntity<AffectationDto.Reponse> couranteDuChauffeur(@PathVariable Long chauffeurId) {
        return ResponseEntity.of(service.couranteDuChauffeur(chauffeurId));
    }

    @PostMapping
    @PreAuthorize(GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Affecte un chauffeur a un camion")
    public AffectationDto.Reponse affecter(@Valid @RequestBody AffectationDto.Requete requete) {
        return service.affecter(requete);
    }

    @PatchMapping("/{id}/cloture")
    @PreAuthorize(GESTION)
    @Operation(summary = "Cloture une affectation en cours")
    public AffectationDto.Reponse cloturer(@PathVariable Long id,
                                           @Valid @RequestBody AffectationDto.Cloture cloture) {
        return service.cloturer(id, cloture);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','GESTIONNAIRE_PARC')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprime une affectation future non commencee")
    public void supprimer(@PathVariable Long id) {
        service.supprimer(id);
    }
}
