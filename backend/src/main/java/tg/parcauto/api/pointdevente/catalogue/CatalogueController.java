package tg.parcauto.api.pointdevente.catalogue;

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
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.Autorisations;

import java.util.List;

/** Catalogue du point de vente : la caisse le lit, le gerant le tient. */
@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@RequestMapping("/api/pdv")
@RequiredArgsConstructor
@Tag(name = "Point de vente - catalogue")
public class CatalogueController {

    private final CatalogueService service;

    @GetMapping("/categories")
    @Operation(summary = "Categories d'articles")
    public List<CatalogueDto.CategorieReponse> categories() {
        return service.categories();
    }

    @PostMapping("/categories")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cree une categorie")
    public CatalogueDto.CategorieReponse creerCategorie(@Valid @RequestBody CatalogueDto.CategorieRequete r) {
        return service.creerCategorie(r);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Modifie une categorie")
    public CatalogueDto.CategorieReponse modifierCategorie(@PathVariable Long id,
                                                           @Valid @RequestBody CatalogueDto.CategorieRequete r) {
        return service.modifierCategorie(id, r);
    }

    @GetMapping("/articles")
    @Operation(summary = "Liste paginee des articles")
    public Page<CatalogueDto.ArticleReponse> articles(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categorieId,
            @RequestParam(required = false) Boolean actif,
            @RequestParam(required = false) Boolean stockBas,
            /** Publie (vrai) ou non (faux) dans la boutique en ligne ; absent = tous. */
            @RequestParam(required = false) Boolean enLigne,
            @PageableDefault(size = 20, sort = "nom", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.rechercher(q, categorieId, actif, stockBas, enLigne, pageable);
    }

    @GetMapping("/articles/caisse")
    @Operation(summary = "Articles actifs proposes a l'ecran de caisse")
    public List<CatalogueDto.ArticleReponse> catalogueCaisse() {
        return service.catalogueCaisse();
    }

    @GetMapping("/articles/code/{code}")
    @Operation(summary = "Article lu a la douchette (code-barres ou code interne)")
    public CatalogueDto.ArticleReponse parCode(@PathVariable String code) {
        return service.parCode(code);
    }

    @GetMapping("/articles/{id}")
    @Operation(summary = "Fiche d'un article")
    public CatalogueDto.ArticleReponse article(@PathVariable Long id) {
        return service.parId(id);
    }

    @PostMapping("/articles")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cree un article ; le code est genere s'il n'est pas fourni")
    public CatalogueDto.ArticleReponse creer(@Valid @RequestBody CatalogueDto.ArticleRequete r) {
        return service.creer(r);
    }

    @PutMapping("/articles/{id}")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Modifie un article (le stock se modifie par un mouvement)")
    public CatalogueDto.ArticleReponse modifier(@PathVariable Long id,
                                                @Valid @RequestBody CatalogueDto.ArticleRequete r) {
        return service.modifier(id, r);
    }

    @DeleteMapping("/articles/{id}")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive un article")
    public void archiver(@PathVariable Long id) {
        service.archiver(id);
    }
}
