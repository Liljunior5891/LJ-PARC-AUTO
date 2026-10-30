package tg.parcauto.api.finance.recette;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;
import tg.parcauto.api.security.Autorisations;

@RestController
@RequestMapping("/api/recettes")
@RequiredArgsConstructor
@Tag(name = "Recettes")
public class RecetteController {


    private final RecetteService service;

    @GetMapping
    @Operation(summary = "Liste paginee des recettes")
    public Page<RecetteDto.Reponse> lister(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TypeRecette type,
            @RequestParam(required = false) Long camionId,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @PageableDefault(size = 20, sort = "dateRecette", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.rechercher(q, type, camionId, clientId, debut, fin, pageable);
    }

    @GetMapping("/repartition")
    @Operation(summary = "Repartition des recettes par type sur une periode")
    public List<RecetteDto.LigneType> repartition(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) Long camionId) {
        return service.repartitionParType(debut, fin, camionId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'une recette")
    public RecetteDto.Reponse detail(@PathVariable Long id) {
        return service.parId(id);
    }

    @PostMapping
    @PreAuthorize(Autorisations.GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistre une recette rattachee a un vehicule")
    public RecetteDto.Reponse creer(@Valid @RequestBody RecetteDto.Requete requete) {
        return service.creer(requete);
    }

    @PostMapping("/location")
    @PreAuthorize(Autorisations.GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistre une recette de location et le contrat correspondant")
    public RecetteDto.Reponse enregistrerLocation(
            @Valid @RequestBody RecetteDto.RequeteLocation demande) {
        return service.enregistrerLocation(demande);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Met a jour une recette")
    public RecetteDto.Reponse modifier(@PathVariable Long id, @Valid @RequestBody RecetteDto.Requete requete) {
        return service.modifier(id, requete);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorisations.GESTION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprime une recette non facturee")
    public void supprimer(@PathVariable Long id) {
        service.supprimer(id);
    }
}
