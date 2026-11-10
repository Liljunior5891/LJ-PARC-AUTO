package tg.parcauto.api.plateforme.abonnement;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.security.Autorisations;

import java.util.List;

/** Console de l'exploitant : formules (prix par module), reglages, paiements et abonnements des entreprises. */
@RestController
@RequestMapping("/api/plateforme/abonnements")
@RequiredArgsConstructor
@PreAuthorize(Autorisations.PLATEFORME)
@Tag(name = "Plateforme - abonnements")
public class AbonnementPlateformeController {

    private final AbonnementService service;

    public record Rejet(String motif) {
    }

    @GetMapping
    @Operation(summary = "Abonnement de chaque entreprise : etat, echeance, formule et consommation")
    public List<AbonnementService.AbonnementVue> abonnements() {
        return service.tousLesAbonnements();
    }

    @GetMapping("/entreprises/{id}")
    public AbonnementService.AbonnementVue abonnement(@PathVariable Long id) {
        return service.vueAbonnement(id);
    }

    @GetMapping("/entreprises/{id}/paiements")
    public List<AbonnementService.PaiementVue> paiementsEntreprise(@PathVariable Long id) {
        return service.paiementsDe(id);
    }

    @PostMapping("/entreprises/{id}/paiements")
    @Operation(summary = "Enregistre un paiement recu ; il est valide aussitot")
    public AbonnementService.PaiementVue enregistrer(@PathVariable Long id,
                                                     @Valid @RequestBody AbonnementService.Saisie saisie,
                                                     Authentication auth) {
        return service.enregistrer(id, saisie, auth.getName());
    }

    @PostMapping("/entreprises/{id}/prolongation")
    @Operation(summary = "Prolonge l'echeance (essai compris) sans paiement")
    public AbonnementService.AbonnementVue prolonger(@PathVariable Long id, @RequestParam int jours) {
        return service.prolonger(id, jours);
    }

    // ---------------------------------------------------------- formules

    @GetMapping("/formules")
    public List<AbonnementService.FormuleVue> formules() {
        return service.formules(false);
    }

    @PostMapping("/formules")
    public AbonnementService.FormuleVue creerFormule(@Valid @RequestBody AbonnementService.FormuleRequete r) {
        return service.creerFormule(r);
    }

    @PutMapping("/formules/{id}")
    public AbonnementService.FormuleVue modifierFormule(@PathVariable Long id,
                                                        @Valid @RequestBody AbonnementService.FormuleRequete r) {
        return service.modifierFormule(id, r);
    }

    @DeleteMapping("/formules/{id}")
    public void supprimerFormule(@PathVariable Long id) {
        service.supprimerFormule(id);
    }

    // ----------------------------------------------------------- reglages

    @GetMapping("/parametres")
    public AbonnementService.ParametresVue parametres() {
        return service.parametresVue();
    }

    @PutMapping("/parametres")
    public AbonnementService.ParametresVue modifierParametres(@Valid @RequestBody AbonnementService.ParametresRequete r) {
        return service.modifierParametres(r);
    }

    @PostMapping("/devis")
    public AbonnementService.Devis devis(@Valid @RequestBody AbonnementService.DemandeDevis demande) {
        return service.devis(demande);
    }

    // --------------------------------------------------------- paiements

    @GetMapping("/paiements")
    public List<AbonnementService.PaiementVue> paiements() {
        return service.tousLesPaiements();
    }

    @PostMapping("/paiements/{id}/validation")
    @Operation(summary = "Valide un paiement declare : ouvre la periode et numerote la facture")
    public AbonnementService.PaiementVue valider(@PathVariable Long id, Authentication auth) {
        return service.valider(id, auth.getName());
    }

    @PostMapping("/paiements/{id}/rejet")
    public AbonnementService.PaiementVue rejeter(@PathVariable Long id, @RequestBody Rejet rejet, Authentication auth) {
        return service.rejeter(id, rejet.motif(), auth.getName());
    }

    @GetMapping("/paiements/{id}/preuve")
    public ResponseEntity<Resource> preuve(@PathVariable Long id) {
        return AbonnementController.fichier(service, service.paiementAvecPreuve(null, id));
    }
}
