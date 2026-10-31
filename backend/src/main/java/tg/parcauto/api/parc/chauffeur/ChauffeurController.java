package tg.parcauto.api.parc.chauffeur;

import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import tg.parcauto.api.security.Autorisations;

@RestController
@ModuleRequis(ModuleApplicatif.GESTION_PARC)
@RequestMapping("/api/chauffeurs")
@RequiredArgsConstructor
@Tag(name = "Chauffeurs")
public class ChauffeurController {


    private final ChauffeurService service;

    @GetMapping
    @Operation(summary = "Liste paginee des chauffeurs")
    public Page<ChauffeurDto.Reponse> lister(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) StatutChauffeur statut,
                                             @RequestParam(required = false) Boolean actif,
                                             @PageableDefault(size = 20, sort = "nom",
                                                     direction = Sort.Direction.ASC) Pageable pageable) {
        return service.rechercher(q, statut, actif, pageable);
    }

    @GetMapping("/alertes/permis")
    @Operation(summary = "Chauffeurs dont le permis arrive a expiration")
    public List<ChauffeurDto.Reponse> permisAExpirer(@RequestParam(defaultValue = "30") int jours) {
        return service.permisAExpirer(jours);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fiche detaillee d'un chauffeur")
    public ChauffeurDto.Reponse detail(@PathVariable Long id) {
        return service.parId(id);
    }

    @PostMapping
    @PreAuthorize(Autorisations.GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cree un chauffeur")
    public ChauffeurDto.Reponse creer(@Valid @RequestBody ChauffeurDto.Requete requete) {
        return service.creer(requete);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Met a jour la fiche d'un chauffeur")
    public ChauffeurDto.Reponse modifier(@PathVariable Long id, @Valid @RequestBody ChauffeurDto.Requete requete) {
        return service.modifier(id, requete);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorisations.GESTION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive un chauffeur (suppression logique)")
    public void archiver(@PathVariable Long id) {
        service.archiver(id);
    }
}
