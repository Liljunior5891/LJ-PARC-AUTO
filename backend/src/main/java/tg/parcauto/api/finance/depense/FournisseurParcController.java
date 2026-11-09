package tg.parcauto.api.finance.depense;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

/** Fournisseurs du parc, leurs dettes (depenses a payer) et les reglements. */
@RestController
@ModuleRequis(ModuleApplicatif.GESTION_PARC)
@RequestMapping("/api/parc/fournisseurs")
@PreAuthorize(Autorisations.GESTION)
@RequiredArgsConstructor
@Tag(name = "Parc - fournisseurs et dettes")
public class FournisseurParcController {

    private final FournisseurParcService service;

    @GetMapping
    @Operation(summary = "Fournisseurs avec leurs achats et leur solde du")
    public List<FournisseurParcService.Reponse> lister() {
        return service.lister();
    }

    @GetMapping("/dettes")
    @Operation(summary = "Dettes ouvertes, de tous les fournisseurs ou d'un seul")
    public List<DepenseDto.Reponse> dettes(@RequestParam(required = false) Long fournisseurId) {
        return service.dettes(fournisseurId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fiche : dettes ouvertes, depenses et reglements")
    public FournisseurParcService.Fiche fiche(@PathVariable Long id) {
        return service.fiche(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FournisseurParcService.Reponse creer(@Valid @RequestBody FournisseurParcService.Requete r) {
        return service.enregistrer(null, r);
    }

    @PutMapping("/{id}")
    public FournisseurParcService.Reponse modifier(@PathVariable Long id, @Valid @RequestBody FournisseurParcService.Requete r) {
        return service.enregistrer(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprime une fiche sans depense")
    public void supprimer(@PathVariable Long id) {
        service.supprimer(id);
    }

    @PostMapping("/reglements")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Regle tout ou partie d'une dette")
    public FournisseurParcService.ReglementReponse regler(@Valid @RequestBody FournisseurParcService.ReglementRequete r) {
        return service.regler(r);
    }

    @PostMapping("/reglements/{id}/annulation")
    @Operation(summary = "Annule un reglement, avec un motif : la dette remonte d'autant")
    public FournisseurParcService.ReglementReponse annulerReglement(@PathVariable Long id,
                                                                    @Valid @RequestBody DepenseDto.Motif motif) {
        return service.annulerReglement(id, motif.motif());
    }
}
