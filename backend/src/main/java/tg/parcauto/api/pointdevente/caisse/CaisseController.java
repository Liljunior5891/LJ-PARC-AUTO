package tg.parcauto.api.pointdevente.caisse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.Autorisations;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;

/**
 * Sessions de caisse : un gerant les ouvre et les ferme (billettage, comptage
 * a l'aveugle) ; le personnel de la caisse encaisse dessus et y enregistre les
 * entrees et sorties d'especes.
 */
@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@RequestMapping("/api/pdv/sessions")
@RequiredArgsConstructor
@Tag(name = "Point de vente - caisses")
public class CaisseController {

    private final CaisseService service;
    private final RapportSessionPdf rapportPdf;

    @GetMapping("/coupures")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Billets et pieces a compter, de la plus forte valeur a la plus faible")
    public List<BigDecimal> coupures() {
        return Coupure.VALEURS;
    }

    @GetMapping("/ouvertes")
    @PreAuthorize(Autorisations.PDV_SESSIONS)
    @Operation(summary = "Sessions ouvertes sur lesquelles l'utilisateur peut encaisser")
    public List<CaisseService.Reponse> ouvertes(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.ouvertesPour(principal);
    }

    @GetMapping("/courante")
    @PreAuthorize(Autorisations.PDV_SESSIONS)
    @Operation(summary = "Session choisie par le poste, ou la seule ouverte a l'utilisateur ; 204 sinon")
    public ResponseEntity<CaisseService.Reponse> courante(@RequestParam(required = false) Long sessionId,
                                                          @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.courante(principal, sessionId).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @PostMapping
    @PreAuthorize(Autorisations.PDV_GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Un gerant ouvre la session d'une caisse, fond compte par coupure")
    public CaisseService.Reponse ouvrir(@Valid @RequestBody CaisseService.Ouverture ouverture,
                                        @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.ouvrir(ouverture, principal);
    }

    @PostMapping("/{id}/mouvements")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Entree ou sortie d'especes hors vente, avec son motif")
    public CaisseService.Reponse mouvement(@PathVariable Long id, @Valid @RequestBody CaisseService.Mouvement mouvement,
                                           @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.mouvement(id, mouvement, principal);
    }

    @PostMapping("/{id}/controle")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Comptage a l'aveugle : especes attendues et ecart, sans fermer")
    public CaisseService.Controle controler(@PathVariable Long id, @Valid @RequestBody CaisseService.Comptage comptage) {
        return service.controler(id, comptage);
    }

    @PostMapping("/{id}/cloture")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Ferme la session : plus de commande a regler, ecart justifie s'il n'est pas nul")
    public CaisseService.Reponse fermer(@PathVariable Long id, @Valid @RequestBody CaisseService.Cloture cloture,
                                        @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.fermer(id, cloture, principal);
    }

    @GetMapping
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Historique des sessions, de toutes les caisses ou d'une caisse")
    public Page<CaisseService.Reponse> lister(@RequestParam(required = false) Long caisseId,
                                              @PageableDefault(size = 20) Pageable pageable) {
        return service.lister(caisseId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Rapport d'une session : totaux par moyen de paiement, mouvements, billettage et ecart")
    public CaisseService.Reponse rapport(@PathVariable Long id) {
        return service.rapport(id);
    }

    @GetMapping("/{id}/produits")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Ventes de la session regroupees par produit")
    public List<CaisseService.VenteProduit> parProduit(@PathVariable Long id) {
        return service.parProduit(id);
    }

    @GetMapping("/{id}/rapport-pdf")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Rapport d'activite de la session en PDF")
    public ResponseEntity<byte[]> rapportPdf(@PathVariable Long id,
                                             @RequestParam(defaultValue = "Africa/Lome") String fuseau) {
        ZoneId zone;
        try {
            zone = ZoneId.of(fuseau);
        } catch (DateTimeException e) {
            throw new BusinessException("Fuseau horaire inconnu : " + fuseau);
        }
        CaisseService.Reponse session = service.rapport(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("rapport-caisse-" + session.numero() + ".pdf").build().toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .body(rapportPdf.generer(id, zone));
    }
}
