package tg.parcauto.api.finance.depense;

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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import tg.parcauto.api.security.Autorisations;

@RestController
@RequestMapping("/api/depenses")
@RequiredArgsConstructor
@Tag(name = "Depenses")
public class DepenseController {


    private final DepenseService service;

    @GetMapping
    @Operation(summary = "Liste paginee des depenses")
    public Page<DepenseDto.Reponse> lister(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CategorieDepense categorie,
            @RequestParam(required = false) SousCategorieDepense sousCategorie,
            @RequestParam(required = false) Long camionId,
            @RequestParam(required = false) Long chauffeurId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @PageableDefault(size = 20, sort = "dateDepense", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.rechercher(q, categorie, sousCategorie, camionId, chauffeurId, debut, fin, pageable);
    }

    @GetMapping("/natures")
    @Operation(summary = "Natures de depenses disponibles, groupees par categorie")
    public Map<CategorieDepense, List<Map<String, String>>> natures() {
        return Arrays.stream(CategorieDepense.values())
                .collect(java.util.stream.Collectors.toMap(
                        categorie -> categorie,
                        categorie -> SousCategorieDepense.de(categorie).stream()
                                .map(nature -> Map.of("valeur", nature.name(), "libelle", nature.getLibelle()))
                                .toList(),
                        (a, b) -> a,
                        () -> new java.util.EnumMap<>(CategorieDepense.class)));
    }

    @GetMapping("/repartition")
    @Operation(summary = "Depenses par categorie sur une periode (chapitre 17)")
    public List<DepenseDto.LigneCategorie> repartition(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) Long camionId) {
        return service.repartitionParCategorie(debut, fin, camionId);
    }

    @GetMapping("/repartition/natures")
    @Operation(summary = "Depenses d'un camion par nature")
    public List<DepenseDto.LigneNature> repartitionNatures(
            @RequestParam Long camionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return service.repartitionParNature(camionId, debut, fin);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'une depense")
    public DepenseDto.Reponse detail(@PathVariable Long id) {
        return service.parId(id);
    }

    @PostMapping
    @PreAuthorize(Autorisations.GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistre une depense rattachee a un camion")
    public DepenseDto.Reponse creer(@Valid @RequestBody DepenseDto.Requete requete) {
        return service.creer(requete);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Met a jour une depense")
    public DepenseDto.Reponse modifier(@PathVariable Long id, @Valid @RequestBody DepenseDto.Requete requete) {
        return service.modifier(id, requete);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorisations.GESTION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprime une depense")
    public void supprimer(@PathVariable Long id) {
        service.supprimer(id);
    }
}
