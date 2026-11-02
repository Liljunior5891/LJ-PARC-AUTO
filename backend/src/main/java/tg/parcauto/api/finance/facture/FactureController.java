package tg.parcauto.api.finance.facture;

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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

import java.time.LocalDate;
import java.util.List;
import tg.parcauto.api.security.Autorisations;

@RestController
@ModuleRequis(ModuleApplicatif.GESTION_PARC)
@RequestMapping("/api/factures")
@RequiredArgsConstructor
@Tag(name = "Facturation et paiements")
public class FactureController {


    private final FactureService service;
    private final FacturePdfService pdfService;

    @GetMapping
    @Operation(summary = "Liste paginee des factures")
    public Page<FactureDto.Reponse> lister(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) StatutFacture statut,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            /** Canal : vrai = en ligne (Marketplace), faux = agence ; absent = tous. */
            @RequestParam(required = false) Boolean enLigne,
            @PageableDefault(size = 20, sort = "dateEmission", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.rechercher(q, statut, clientId, debut, fin, enLigne, pageable);
    }

    @GetMapping("/impayees")
    @Operation(summary = "Factures emises non soldees")
    public List<FactureDto.Reponse> impayees() {
        return service.impayees();
    }

    @GetMapping("/creances")
    @Operation(summary = "Etat des creances : facture, encaisse, restant du")
    public FactureDto.Creances creances() {
        return service.creances();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'une facture avec ses lignes et ses reglements")
    public FactureDto.Reponse detail(@PathVariable Long id) {
        return service.parId(id);
    }

    /** Edition PDF a remettre au client (chapitre 27). */
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Telecharge la facture au format PDF")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + pdfService.nomFichier(id) + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfService.produire(id));
    }

    @PostMapping
    @PreAuthorize(Autorisations.GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cree une facture en brouillon")
    public FactureDto.Reponse creer(@Valid @RequestBody FactureDto.Requete requete) {
        return service.creer(requete);
    }

    @PostMapping("/depuis-contrat")
    @PreAuthorize(Autorisations.GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Genere une facture a partir d'un contrat de location")
    public FactureDto.Reponse depuisContrat(@Valid @RequestBody FactureDto.DepuisContrat requete) {
        return service.genererDepuisContrat(requete);
    }

    @PostMapping("/depuis-mission")
    @PreAuthorize(Autorisations.GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Genere une facture a partir d'une mission terminee")
    public FactureDto.Reponse depuisMission(@Valid @RequestBody FactureDto.DepuisMission requete) {
        return service.genererDepuisMission(requete);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Met a jour une facture en brouillon")
    public FactureDto.Reponse modifier(@PathVariable Long id, @Valid @RequestBody FactureDto.Requete requete) {
        return service.modifier(id, requete);
    }

    @PatchMapping("/{id}/emission")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Emet la facture et enregistre les recettes par vehicule")
    public FactureDto.Reponse emettre(@PathVariable Long id) {
        return service.emettre(id);
    }

    @PostMapping("/{id}/paiements")
    @PreAuthorize(Autorisations.GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistre un reglement")
    public FactureDto.Reponse payer(@PathVariable Long id,
                                    @Valid @RequestBody FactureDto.PaiementRequete requete) {
        return service.enregistrerPaiement(id, requete);
    }

    @PatchMapping("/{id}/annulation")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Annule une facture sans reglement")
    public FactureDto.Reponse annuler(@PathVariable Long id, @RequestParam(required = false) String motif) {
        return service.annuler(id, motif);
    }

    @PatchMapping("/retards")
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Bascule en retard les factures echues non soldees")
    public int marquerRetards() {
        return service.marquerRetards();
    }
}
