package tg.parcauto.api.parc.camion;

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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import tg.parcauto.api.security.Autorisations;

@RestController
@RequestMapping("/api/camions")
@RequiredArgsConstructor
@Tag(name = "Vehicules")
public class CamionController {

    /** Roles autorises a modifier le parc. */

    private final CamionService service;
    private final OdometreService odometreService;

    @GetMapping
    @Operation(summary = "Liste paginee des vehicules avec recherche et filtres")
    public Page<CamionDto.Reponse> lister(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) StatutCamion statut,
                                          @RequestParam(required = false) Boolean actif,
                                          @PageableDefault(size = 20, sort = "immatriculation",
                                                  direction = Sort.Direction.ASC) Pageable pageable) {
        return service.rechercher(q, statut, actif, pageable);
    }

    @GetMapping("/marques")
    @Operation(summary = "Referentiel des marques de vehicules, avec logo et couleur")
    public List<MarqueCamionDto.Reponse> marques() {
        return service.marques();
    }

    @GetMapping("/types")
    @Operation(summary = "Referentiel des types de vehicules")
    public List<ReferentielDto> types() {
        return service.types();
    }

    @GetMapping("/couleurs")
    @Operation(summary = "Referentiel des couleurs")
    public List<ReferentielDto> couleurs() {
        return service.couleurs();
    }

    @GetMapping("/statistiques/statuts")
    @Operation(summary = "Repartition des vehicules actifs par statut")
    public Map<StatutCamion, Long> repartition() {
        return service.repartitionParStatut();
    }

    /** Historique des releves kilometriques, tous modules confondus. */
    @GetMapping("/{id}/odometre")
    @Operation(summary = "Historique du compteur kilometrique d'un vehicule")
    public OdometreDto odometre(
            @PathVariable Long id,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate debut,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate fin) {
        java.time.LocalDate finRetenue = fin == null ? java.time.LocalDate.now() : fin;
        return odometreService.historique(id,
                debut == null ? finRetenue.minusMonths(6) : debut, finRetenue);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fiche detaillee d'un vehicule")
    public CamionDto.Reponse detail(@PathVariable Long id) {
        return service.parId(id);
    }

    @PostMapping
    @PreAuthorize(Autorisations.GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cree un vehicule")
    public CamionDto.Reponse creer(@Valid @RequestBody CamionDto.Requete requete) {
        return service.creer(requete);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Met a jour la fiche d'un vehicule")
    public CamionDto.Reponse modifier(@PathVariable Long id, @Valid @RequestBody CamionDto.Requete requete) {
        return service.modifier(id, requete);
    }

    @PatchMapping("/{id}/kilometrage")
    @PreAuthorize(Autorisations.TERRAIN)
    @Operation(summary = "Met a jour le compteur kilometrique")
    public CamionDto.Reponse majKilometrage(@PathVariable Long id,
                                            @Valid @RequestBody CamionDto.MajKilometrage requete) {
        return service.majKilometrage(id, requete.kilometrage());
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Change le statut d'exploitation du vehicule")
    public CamionDto.Reponse majStatut(@PathVariable Long id, @RequestBody CamionDto.MajStatut requete) {
        return service.majStatut(id, requete.statut());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorisations.GESTION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive un vehicule (suppression logique)")
    public void archiver(@PathVariable Long id) {
        service.archiver(id);
    }
}
