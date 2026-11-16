package tg.parcauto.api.pointdevente.associe;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.Autorisations;
import tg.parcauto.api.security.UtilisateurPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.time.Instant;
import java.util.List;

/** Associes du point de vente : rattachements (administrateur) et part des benefices (chaque associe voit la sienne). */
@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@tg.parcauto.api.plateforme.module.OuvertAuxAssocies
@RequestMapping("/api/pdv/associes")
@PreAuthorize(Autorisations.ADMINISTRATION)
@RequiredArgsConstructor
@Tag(name = "Point de vente - associes")
public class AssocieController {

    private final AssocieService service;
    private final CompteAssocieService comptes;

    @GetMapping
    @Operation(summary = "Comptes au profil associe et leurs rattachements")
    public List<AssocieService.Associe> associes() {
        return service.associes();
    }

    @PutMapping("/{id}/parts")
    @Operation(summary = "Remplace les produits et categories d'un associe, avec leurs pourcentages")
    public AssocieService.Associe remplacer(@PathVariable Long id, @Valid @RequestBody AssocieService.Parts parts) {
        return service.remplacerParts(id, parts.parts());
    }

    @GetMapping("/benefices")
    @PreAuthorize("hasAnyRole('ADMIN','ASSOCIE')")
    @Operation(summary = "Part de chaque associe sur la marge de ses produits ; un associe ne voit que la sienne")
    public AssocieService.Benefices benefices(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fin,
            @org.springframework.security.core.annotation.AuthenticationPrincipal tg.parcauto.api.security.UtilisateurPrincipal principal) {
        return service.benefices(debut, fin, principal);
    }

    // ------------------------------------------------- sous-compte et depots

    @GetMapping("/compte")
    @PreAuthorize("hasRole('ASSOCIE')")
    @Operation(summary = "Sous-compte de l'associe connecte : parts mensuelles, depots, apports au fonds")
    public CompteAssocieService.Compte monCompte(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return comptes.monCompte(principal);
    }

    @GetMapping("/compte/a-valider")
    @PreAuthorize("hasRole('ASSOCIE')")
    @Operation(summary = "Nombre de depots qui attendent la decision de l'associe connecte")
    public long aValider(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return comptes.aValider(principal);
    }

    @GetMapping("/fonds")
    @PreAuthorize("hasRole('ASSOCIE')")
    @Operation(summary = "Fonds de reserve vu par l'associe : solde, ses apports, depenses")
    public CompteAssocieService.FondsAssocie fonds(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return comptes.fonds(principal);
    }

    @PostMapping("/compte/apports")
    @PreAuthorize("hasRole('ASSOCIE')")
    @Operation(summary = "Apport de l'associe au fonds de reserve, depuis son sous-compte")
    public tg.parcauto.api.pointdevente.reserve.FondsReserveService.Apport apporter(
            @Valid @RequestBody CompteAssocieService.ApportRequete demande,
            @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return comptes.apporter(demande, principal);
    }

    @PostMapping("/depots/{id}/validation")
    @PreAuthorize("hasRole('ASSOCIE')")
    @Operation(summary = "L'associe confirme le depot : son sous-compte est credite")
    public CompteAssocieService.Depot valider(@PathVariable Long id,
                                              @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return comptes.valider(id, principal);
    }

    @PostMapping("/depots/{id}/refus")
    @PreAuthorize("hasRole('ASSOCIE')")
    @Operation(summary = "L'associe conteste le depot, avec un motif")
    public CompteAssocieService.Depot refuser(@PathVariable Long id, @Valid @RequestBody CompteAssocieService.Refus refus,
                                              @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return comptes.refuser(id, refus.motif(), principal);
    }

    @GetMapping("/depots/{id}/piece")
    @PreAuthorize("hasAnyRole('ADMIN','GERANT','ASSOCIE')")
    @Operation(summary = "Bordereau du depot ; l'associe ne voit que les siens")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> piece(
            @PathVariable Long id, @AuthenticationPrincipal UtilisateurPrincipal principal) {
        DepotAssocie d = comptes.depotAvecPiece(id, principal);
        String nom = d.getPieceNom() == null ? "bordereau-" + d.getNumero() : d.getPieceNom();
        return tg.parcauto.api.common.FichierServi.reponse(comptes.piece(d), nom, true);
    }

    @GetMapping("/versements")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Reste a verser, versements et sous-compte de chaque associe")
    public CompteAssocieService.Synthese versements() {
        return comptes.synthese();
    }

    @GetMapping("/{id}/compte")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Sous-compte d'un associe")
    public CompteAssocieService.Compte compte(@PathVariable Long id) {
        return comptes.compteDe(id);
    }

    @PostMapping("/depots")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Saisit le depot en banque de la part d'un associe (bordereau a joindre ensuite)")
    public CompteAssocieService.Depot creerDepot(@Valid @RequestBody CompteAssocieService.DepotRequete demande,
                                                 @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return comptes.creerDepot(demande, principal);
    }

    @PutMapping("/depots/{id}")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Corrige un depot en attente ou refuse ; il repart vers l'associe")
    public CompteAssocieService.Depot corriger(@PathVariable Long id,
                                               @Valid @RequestBody CompteAssocieService.DepotRequete demande,
                                               @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return comptes.corriger(id, demande, principal);
    }

    @PostMapping("/depots/{id}/annulation")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Annule un depot en attente ou refuse")
    public CompteAssocieService.Depot annuler(@PathVariable Long id,
                                              @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return comptes.annuler(id, principal);
    }

    @PostMapping(value = "/depots/{id}/piece", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Joint le bordereau de depot")
    public CompteAssocieService.Depot joindrePiece(@PathVariable Long id,
                                                   @org.springframework.web.bind.annotation.RequestPart("fichier") org.springframework.web.multipart.MultipartFile fichier,
                                                   @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return comptes.joindrePiece(id, fichier, principal);
    }
}
