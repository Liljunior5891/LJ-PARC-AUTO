package tg.parcauto.api.pointdevente.restauration;

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

/** Salles, tables et plan de service d'un bar ou d'un restaurant. */
@RestController
@ModuleRequis(value = ModuleApplicatif.POINT_DE_VENTE, variante = "RESTAURATION")
@RequestMapping("/api/pdv/restauration")
@RequiredArgsConstructor
@Tag(name = "Point de vente - salles et tables")
public class SalleController {

    private final SalleService service;
    private final ReglageService reglageService;

    @GetMapping("/plan")
    @PreAuthorize(Autorisations.PDV_SALLE)
    @Operation(summary = "Plan de service : salles, tables et commandes en cours")
    public RestaurationDto.Plan plan(
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            tg.parcauto.api.security.UtilisateurPrincipal principal) {
        return service.plan(principal);
    }

    @GetMapping("/salles")
    @PreAuthorize(Autorisations.PDV_SALLE)
    @Operation(summary = "Salles du restaurant")
    public List<RestaurationDto.SalleReponse> salles() {
        return service.salles();
    }

    @PostMapping("/salles")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cree une salle")
    public RestaurationDto.SalleReponse creerSalle(@Valid @RequestBody RestaurationDto.SalleRequete requete) {
        return service.creerSalle(requete);
    }

    @PutMapping("/salles/{id}")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Modifie une salle")
    public RestaurationDto.SalleReponse modifierSalle(@PathVariable Long id,
                                                      @Valid @RequestBody RestaurationDto.SalleRequete requete) {
        return service.modifierSalle(id, requete);
    }

    @GetMapping("/tables")
    @PreAuthorize(Autorisations.PDV_SALLE)
    @Operation(summary = "Tables de toutes les salles")
    public List<RestaurationDto.TableReponse> tables() {
        return service.tables();
    }

    @PostMapping("/tables")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajoute une table au plan")
    public RestaurationDto.TableReponse creerTable(@Valid @RequestBody RestaurationDto.TableRequete requete) {
        return service.creerTable(requete);
    }

    @PutMapping("/tables/{id}")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Modifie une table")
    public RestaurationDto.TableReponse modifierTable(@PathVariable Long id,
                                                      @Valid @RequestBody RestaurationDto.TableRequete requete) {
        return service.modifierTable(id, requete);
    }

    @PutMapping("/tables/positions")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Enregistre les positions des tables apres l'edition du plan")
    public List<RestaurationDto.TableReponse> placer(
            @RequestBody List<RestaurationDto.@Valid PositionTable> positions) {
        return service.placer(positions);
    }

    @GetMapping(value = "/tables/{id}/qr", produces = "image/svg+xml")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Code QR de la table (SVG), vers la carte en ligne servie a l'adresse donnee")
    public String codeQr(@PathVariable Long id, @RequestParam String origine) {
        return reglageService.codeQr(id, origine);
    }

    @PostMapping("/tables/{id}/jeton")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Renouvelle le code QR de la table ; les anciens codes ne fonctionnent plus")
    public RestaurationDto.TableReponse regenererJeton(@PathVariable Long id) {
        return reglageService.regenererJeton(id);
    }

    @GetMapping("/reglages")
    @PreAuthorize(Autorisations.PDV_SALLE)
    @Operation(summary = "Reglages de la commande par code QR")
    public RestaurationDto.Reglages reglages() {
        return reglageService.lire();
    }

    @PutMapping("/reglages")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Modifie les reglages de la commande par code QR")
    public RestaurationDto.Reglages modifierReglages(@Valid @RequestBody RestaurationDto.Reglages reglages) {
        return reglageService.modifier(reglages);
    }

    @DeleteMapping("/tables/{id}")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Retire une table du plan")
    public void retirerTable(@PathVariable Long id) {
        service.retirerTable(id);
    }
}
