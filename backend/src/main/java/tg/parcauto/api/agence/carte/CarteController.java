package tg.parcauto.api.agence.carte;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.agence.tresorerie.JourneeCaisse;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.time.LocalDate;
import java.util.List;

/** Cartes prepayees : produits, stock par lot, ventes et suivi de la recharge. */
@RestController
@ModuleRequis(ModuleApplicatif.AGENCES)
@RequestMapping("/api/agences/cartes")
@RequiredArgsConstructor
@Tag(name = "Agences - cartes prepayees")
public class CarteController {

    private final CarteService service;
    private final tg.parcauto.api.agence.horsligne.HorsLigneService horsLigne;

    public record Motif(@NotBlank @Size(max = 500) String motif) {
    }

    @GetMapping("/produits")
    @Operation(summary = "Cartes vendues et leur prix (la Carte Xpress est posee a la premiere lecture)")
    public List<CarteService.ProduitDto> produits() {
        return service.produits();
    }

    @PostMapping("/produits")
    @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajoute un type de carte")
    public CarteService.ProduitDto creerProduit(@Valid @RequestBody CarteService.ProduitRequete r) {
        return service.enregistrerProduit(null, r);
    }

    @PutMapping("/produits/{id}")
    @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")
    @Operation(summary = "Modifie un type de carte (prix, seuil d'alerte)")
    public CarteService.ProduitDto modifierProduit(@PathVariable Long id, @Valid @RequestBody CarteService.ProduitRequete r) {
        return service.enregistrerProduit(id, r);
    }

    @GetMapping("/stock")
    @Operation(summary = "Stock de l'agence par carte : disponible, valeur au cout reel, lots, alerte")
    public List<CarteService.StockProduit> stock(@RequestParam Long agenceId) {
        return service.stock(agenceId);
    }

    @PostMapping("/lots")
    @PreAuthorize("@droitsAgence.a('CARTE_STOCK') and hasAnyRole('ADMIN', 'SUPERVISEUR_AGENCE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Entree d'un lot : achat paye depuis un compte, ou stock initial sans compte")
    public List<CarteService.StockProduit> entrer(@Valid @RequestBody CarteService.Entree e,
                                                  @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.entrer(e, principal);
    }

    @PostMapping("/ventes")
    @PreAuthorize("@droitsAgence.a('TRANSACTION_SAISIR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Vend des cartes : lots les plus anciens d'abord, prix encaisse en caisse")
    public CarteService.VenteDto vendre(@Valid @RequestBody CarteService.Vente v,
                                        @org.springframework.web.bind.annotation.RequestHeader(
                                                value = "X-Cle-Operation", required = false) String cle,
                                        @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return horsLigne.enLigne(cle, tg.parcauto.api.agence.horsligne.OperationHorsLigne.Type.VENTE_CARTE, v, principal,
                () -> service.vendre(v, principal), CarteService.VenteDto::reference);
    }

    @PostMapping("/ventes/{id}/annulation")
    @PreAuthorize("@droitsAgence.a('TRANSACTION_ANNULER')")
    @Operation(summary = "Annule une vente : l'argent sort de la caisse, les cartes reviennent en stock")
    public CarteService.VenteDto annuler(@PathVariable Long id, @Valid @RequestBody Motif motif,
                                         @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.annuler(id, motif.motif(), principal);
    }

    @GetMapping("/ventes")
    @Operation(summary = "Historique des ventes de cartes")
    public Page<CarteService.VenteDto> ventes(@RequestParam(required = false) Long agenceId, @PageableDefault(size = 30) Pageable pageable) {
        return service.lister(agenceId, pageable);
    }

    @GetMapping("/suivi")
    // Chiffre d'affaires, marge et solde de recharge : comme les soldes, pas pour le caissier.
    @org.springframework.security.access.prepost.PreAuthorize("@droitsAgence.a('SOLDES_CONSULTER')")
    @Operation(summary = "Ventes de la periode (chiffre d'affaires, cout, marge) et suivi du compte de recharge")
    public CarteService.Suivi suivi(@RequestParam(required = false) Long agenceId,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au) {
        LocalDate aujourdhui = LocalDate.now(JourneeCaisse.FUSEAU);
        return service.suivi(agenceId, du != null ? du : aujourdhui.withDayOfMonth(1), au != null ? au : aujourdhui);
    }
}
