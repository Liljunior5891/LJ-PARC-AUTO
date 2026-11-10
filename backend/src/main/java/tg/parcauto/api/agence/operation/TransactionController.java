package tg.parcauto.api.agence.operation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.time.LocalDate;
import java.util.List;

/** Transactions des agences : apercu, saisie, annulation et historique. */
@RestController
@ModuleRequis(ModuleApplicatif.AGENCES)
@RequestMapping("/api/agences/transactions")
@RequiredArgsConstructor
@Tag(name = "Agences - transactions")
public class TransactionController {

    private final TransactionService service;
    private final tg.parcauto.api.agence.horsligne.HorsLigneService horsLigne;

    public record Annulation(@NotBlank @Size(max = 500) String motif) {
    }

    @PostMapping("/apercu")
    @PreAuthorize("@droitsAgence.a('TRANSACTION_SAISIR')")
    @Operation(summary = "Effet de la transaction sur les comptes, commission proposee et doublon, sans rien enregistrer")
    public TransactionService.Apercu apercu(@Valid @RequestBody TransactionService.Saisie saisie) {
        return service.apercu(saisie);
    }

    @PostMapping
    @PreAuthorize("@droitsAgence.a('TRANSACTION_SAISIR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Valide une transaction : ecriture au registre (cash, virtuel, commission)")
    public TransactionService.TransactionDto enregistrer(@Valid @RequestBody TransactionService.Saisie saisie,
                                                         @org.springframework.web.bind.annotation.RequestHeader(
                                                                 value = "X-Cle-Operation", required = false) String cle,
                                                         @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return horsLigne.enLigne(cle, tg.parcauto.api.agence.horsligne.OperationHorsLigne.Type.TRANSACTION, saisie, principal,
                () -> service.enregistrer(saisie, principal), TransactionService.TransactionDto::reference);
    }

    @PostMapping("/{id}/annulation")
    @PreAuthorize("@droitsAgence.a('TRANSACTION_ANNULER')")
    @Operation(summary = "Annule une transaction validee par l'ecriture inverse, avec motif")
    public TransactionService.TransactionDto annuler(@PathVariable Long id, @Valid @RequestBody Annulation annulation,
                                                     @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.annuler(id, annulation.motif(), principal);
    }

    @GetMapping
    @Operation(summary = "Historique : ses transactions pour un caissier, toutes avec le droit d'historique complet")
    public Page<TransactionService.TransactionDto> lister(
            @RequestParam(required = false) Long agenceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long reseauId,
            @RequestParam(required = false) Long typeOperationId,
            @RequestParam(required = false) Long caissierId,
            @RequestParam(required = false) TransactionAgence.Statut statut,
            @RequestParam(required = false) Long montantMin,
            @RequestParam(required = false) Long montantMax,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 30) Pageable pageable) {
        return service.lister(new TransactionService.Filtres(agenceId, du, au, serviceId, reseauId, typeOperationId, caissierId,
                statut, montantMin, montantMax, q), pageable);
    }

    @GetMapping("/resume")
    @Operation(summary = "Nombre, volume et commissions des transactions validees qui repondent aux filtres")
    public TransactionService.Resume resume(
            @RequestParam(required = false) Long agenceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long reseauId,
            @RequestParam(required = false) Long typeOperationId,
            @RequestParam(required = false) Long caissierId,
            @RequestParam(required = false) TransactionAgence.Statut statut,
            @RequestParam(required = false) Long montantMin,
            @RequestParam(required = false) Long montantMax,
            @RequestParam(required = false) String q) {
        return service.resume(new TransactionService.Filtres(agenceId, du, au, serviceId, reseauId, typeOperationId, caissierId,
                statut, montantMin, montantMax, q));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'une transaction")
    public TransactionService.TransactionDto detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @GetMapping("/clients")
    @PreAuthorize("@droitsAgence.a('TRANSACTION_SAISIR')")
    @Operation(summary = "Fiches clients dont le telephone ou le nom contient le texte")
    public List<TransactionService.ClientDto> clients(@RequestParam String q) {
        return service.clients(q);
    }
}
