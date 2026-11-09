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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.Autorisations;

import java.util.List;

/**
 * Charges du parc : categories des charges generales (administrateur),
 * depenses recurrentes et mois a payer, reglage de validation.
 */
@RestController
@ModuleRequis(ModuleApplicatif.GESTION_PARC)
@RequestMapping("/api/parc/charges")
@PreAuthorize(Autorisations.GESTION)
@RequiredArgsConstructor
@Tag(name = "Parc - charges, recurrentes et reglage")
public class ChargesParcController {

    private final ChargesParcService service;

    @GetMapping("/reglages")
    @Operation(summary = "Reglages des depenses du parc")
    public ChargesParcService.Reglages reglages() {
        return service.reglages();
    }

    @PutMapping("/reglages")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Active ou desactive la validation des depenses par l'administrateur")
    public ChargesParcService.Reglages modifierReglages(@RequestBody ChargesParcService.Reglages r) {
        return service.modifierReglages(r);
    }

    @GetMapping("/categories")
    @Operation(summary = "Categories des charges generales (liste de base creee a la premiere lecture)")
    public List<ChargesParcService.CategorieReponse> categories() {
        return service.categories();
    }

    @PostMapping("/categories")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @ResponseStatus(HttpStatus.CREATED)
    public ChargesParcService.CategorieReponse creerCategorie(@Valid @RequestBody ChargesParcService.CategorieRequete r) {
        return service.enregistrerCategorie(null, r);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    public ChargesParcService.CategorieReponse modifierCategorie(@PathVariable Long id,
                                                                @Valid @RequestBody ChargesParcService.CategorieRequete r) {
        return service.enregistrerCategorie(id, r);
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprime une categorie jamais utilisee")
    public void supprimerCategorie(@PathVariable Long id) {
        service.supprimerCategorie(id);
    }

    @GetMapping("/recurrentes")
    @Operation(summary = "Modeles de depenses recurrentes")
    public List<ChargesParcService.RecurrenteReponse> recurrentes() {
        return service.recurrentes();
    }

    @PostMapping("/recurrentes")
    @ResponseStatus(HttpStatus.CREATED)
    public ChargesParcService.RecurrenteReponse creerRecurrente(@Valid @RequestBody ChargesParcService.RecurrenteRequete r) {
        return service.enregistrerRecurrente(null, r);
    }

    @PutMapping("/recurrentes/{id}")
    public ChargesParcService.RecurrenteReponse modifierRecurrente(@PathVariable Long id,
                                                                  @Valid @RequestBody ChargesParcService.RecurrenteRequete r) {
        return service.enregistrerRecurrente(id, r);
    }

    @DeleteMapping("/recurrentes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerRecurrente(@PathVariable Long id) {
        service.supprimerRecurrente(id);
    }

    @GetMapping("/a-payer")
    @Operation(summary = "Mois dus et non payes des depenses recurrentes")
    public List<ChargesParcService.APayer> aPayer() {
        return service.aPayer();
    }

    @PostMapping("/recurrentes/{id}/paiement")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Paie un mois d'une depense recurrente (la depense suit la validation)")
    public DepenseDto.Reponse payer(@PathVariable Long id, @Valid @RequestBody ChargesParcService.Paiement p) {
        return service.payer(id, p);
    }
}
