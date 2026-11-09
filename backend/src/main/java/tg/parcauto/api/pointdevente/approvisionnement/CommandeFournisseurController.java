package tg.parcauto.api.pointdevente.approvisionnement;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.util.List;

/** Commandes de reapprovisionnement aupres des fournisseurs. */
@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@RequestMapping("/api/pdv/approvisionnement/commandes")
@PreAuthorize(Autorisations.PDV_GESTION)
@RequiredArgsConstructor
@Tag(name = "Point de vente - reapprovisionnement")
public class CommandeFournisseurController {

    private final CommandeFournisseurService service;
    private final tg.parcauto.api.pointdevente.depense.PiecesAchatPdf pieces;

    @GetMapping
    @Operation(summary = "Commandes fournisseurs, des plus recentes aux plus anciennes")
    public Page<CommandeFournisseurService.Resume> lister(
            @RequestParam(required = false) CommandeFournisseur.Statut statut,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.lister(statut, pageable);
    }

    @GetMapping("/fournisseurs")
    @Operation(summary = "Fournisseurs deja utilises, pour la saisie")
    public List<String> fournisseurs() {
        return service.fournisseurs();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Commande et ses produits")
    public CommandeFournisseurService.Detail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Nouvelle commande fournisseur")
    public CommandeFournisseurService.Detail creer(@Valid @RequestBody CommandeFournisseurService.Requete requete) {
        return service.creer(requete);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifie une commande en cours")
    public CommandeFournisseurService.Detail modifier(@PathVariable Long id,
                                                      @Valid @RequestBody CommandeFournisseurService.Requete requete) {
        return service.modifier(id, requete);
    }

    @PostMapping("/{id}/reception")
    @Operation(summary = "Receptionne la commande : les quantites recues entrent en stock")
    public CommandeFournisseurService.Detail receptionner(@PathVariable Long id,
                                                          @Valid @RequestBody(required = false) CommandeFournisseurService.Reception reception,
                                                          @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.receptionner(id, reception, principal);
    }

    @GetMapping("/{id}/bon-pdf")
    @Operation(summary = "Bon de commande a remettre au fournisseur")
    public org.springframework.http.ResponseEntity<byte[]> bon(@PathVariable Long id) {
        return pdf(pieces.bonCommande(id), pieces.nomBonCommande(id));
    }

    @GetMapping("/{id}/bon-livraison-pdf")
    @Operation(summary = "Bon de livraison : quantites commandees et recues")
    public org.springframework.http.ResponseEntity<byte[]> bonLivraison(@PathVariable Long id) {
        return pdf(pieces.bonLivraison(id), pieces.nomBonLivraison(id));
    }

    private static org.springframework.http.ResponseEntity<byte[]> pdf(byte[] contenu, String nom) {
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nom + "\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(contenu);
    }

    @PostMapping("/{id}/annulation")
    @Operation(summary = "Annule une commande en cours")
    public CommandeFournisseurService.Detail annuler(@PathVariable Long id) {
        return service.annuler(id);
    }
}
