package tg.parcauto.api.pointdevente.restauration;

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
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.util.List;

/** Commandes des tables : prise de commande, envoi, service, addition et encaissement. */
@RestController
@ModuleRequis(value = ModuleApplicatif.POINT_DE_VENTE, variante = "RESTAURATION")
@RequestMapping("/api/pdv/restauration/commandes")
@RequiredArgsConstructor
@Tag(name = "Point de vente - commandes")
public class CommandeController {

    private final CommandeService service;
    private final tg.parcauto.api.pointdevente.caisse.SessionTravailService sessionsTravail;

    @PostMapping
    @PreAuthorize(Autorisations.PDV_SALLE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ouvre une commande sur une table, ou au comptoir avec un nom")
    public RestaurationDto.CommandeDetail ouvrir(@Valid @RequestBody RestaurationDto.Ouverture ouverture,
                                                 @AuthenticationPrincipal UtilisateurPrincipal principal) {
        caisseOuverte();
        return service.ouvrir(ouverture, principal);
    }

    @GetMapping
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Commandes closes ou annulees, des plus recentes aux plus anciennes")
    public Page<RestaurationDto.CommandeResume> historique(
            @RequestParam(defaultValue = "ENCAISSEE") Commande.Statut statut,
            @PageableDefault(size = 30) Pageable pageable) {
        return service.historique(statut, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorisations.PDV_SALLE)
    @Operation(summary = "Commande et ses lignes")
    public RestaurationDto.CommandeDetail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Autorisations.PDV_SALLE)
    @Operation(summary = "Modifie le nombre de couverts et la note de la commande")
    public RestaurationDto.CommandeDetail modifier(@PathVariable Long id,
                                                   @Valid @RequestBody RestaurationDto.Informations infos) {
        caisseOuverte();
        return service.modifierInformations(id, infos);
    }

    @PostMapping("/{id}/lignes")
    @PreAuthorize(Autorisations.PDV_SALLE)
    @Operation(summary = "Ajoute un article en attente d'envoi")
    public RestaurationDto.CommandeDetail ajouter(@PathVariable Long id,
                                                  @Valid @RequestBody RestaurationDto.AjoutLigne ajout) {
        caisseOuverte();
        return service.ajouter(id, ajout);
    }

    @PutMapping("/{id}/lignes/{ligneId}")
    @PreAuthorize(Autorisations.PDV_SALLE)
    @Operation(summary = "Modifie un article pas encore envoye")
    public RestaurationDto.CommandeDetail modifierLigne(@PathVariable Long id, @PathVariable Long ligneId,
                                                        @Valid @RequestBody RestaurationDto.ModificationLigne m) {
        caisseOuverte();
        return service.modifierLigne(id, ligneId, m);
    }

    @DeleteMapping("/{id}/lignes/{ligneId}")
    @PreAuthorize(Autorisations.PDV_SALLE)
    @Operation(summary = "Retire un article pas encore envoye")
    public RestaurationDto.CommandeDetail retirerLigne(@PathVariable Long id, @PathVariable Long ligneId) {
        caisseOuverte();
        return service.retirerLigne(id, ligneId);
    }

    @PostMapping("/{id}/lignes/{ligneId}/annulation")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Annule un article deja envoye et le remet en stock")
    public RestaurationDto.CommandeDetail annulerLigne(@PathVariable Long id, @PathVariable Long ligneId,
                                                       @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.annulerLigne(id, ligneId, principal);
    }

    @PostMapping("/{id}/envoi")
    @PreAuthorize(Autorisations.PDV_SALLE)
    @Operation(summary = "Envoie les articles en attente a la cuisine et au bar")
    public RestaurationDto.CommandeDetail envoyer(@PathVariable Long id) {
        caisseOuverte();
        return service.envoyer(id);
    }

    @PostMapping("/{id}/demandes/validation")
    @PreAuthorize(Autorisations.PDV_SALLE)
    @Operation(summary = "Valide la commande passee par le client depuis le code QR et l'envoie en preparation")
    public RestaurationDto.CommandeDetail validerDemandes(@PathVariable Long id,
                                                          @AuthenticationPrincipal UtilisateurPrincipal principal) {
        caisseOuverte();
        return service.validerDemandes(id, principal);
    }

    @PostMapping("/{id}/demandes/refus")
    @PreAuthorize(Autorisations.PDV_SALLE)
    @Operation(summary = "Ecarte la commande passee par le client depuis le code QR")
    public RestaurationDto.CommandeDetail refuserDemandes(@PathVariable Long id,
                                                          @AuthenticationPrincipal UtilisateurPrincipal principal) {
        caisseOuverte();
        return service.refuserDemandes(id, principal);
    }

    @PostMapping("/{id}/appel/prise-en-compte")
    @PreAuthorize(Autorisations.PDV_SALLE)
    @Operation(summary = "Prend en compte l'appel du client (serveur ou addition)")
    public RestaurationDto.CommandeDetail priseEnCompte(@PathVariable Long id,
                                                        @AuthenticationPrincipal UtilisateurPrincipal principal) {
        caisseOuverte();
        return service.priseEnCompte(id, principal);
    }

    @PostMapping("/{id}/service")
    @PreAuthorize(Autorisations.PDV_SALLE)
    @Operation(summary = "Marque servis les articles prets ; sans selection, tous")
    public RestaurationDto.CommandeDetail servir(@PathVariable Long id,
                                                 @RequestBody(required = false) List<Long> lignes) {
        caisseOuverte();
        return service.servir(id, lignes);
    }

    @PostMapping("/{id}/transfert")
    @PreAuthorize(Autorisations.PDV_SALLE)
    @Operation(summary = "Deplace la commande sur une table libre")
    public RestaurationDto.CommandeDetail transferer(@PathVariable Long id,
                                                     @Valid @RequestBody RestaurationDto.Transfert transfert) {
        caisseOuverte();
        return service.transferer(id, transfert.tableId());
    }

    @PostMapping("/{id}/addition")
    @PreAuthorize(Autorisations.PDV_SALLE)
    @Operation(summary = "Imprime l'addition : la table attend de payer")
    public RestaurationDto.CommandeDetail addition(@PathVariable Long id) {
        caisseOuverte();
        return service.addition(id);
    }

    @PostMapping("/{id}/encaissement")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Encaisse tout ou partie de l'addition sur la caisse ouverte de l'utilisateur")
    public RestaurationDto.ResultatEncaissement encaisser(@PathVariable Long id,
                                                          @Valid @RequestBody RestaurationDto.Encaissement demande,
                                                          @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.encaisser(id, demande, principal);
    }

    @PostMapping("/{id}/annulation")
    @PreAuthorize(Autorisations.PDV_SALLE)
    @Operation(summary = "Annule la commande ; un gerant si des articles sont partis en preparation")
    public RestaurationDto.CommandeDetail annuler(@PathVariable Long id,
                                                  @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.annulerCommande(id, principal);
    }

    /**
     * Rien ne bouge en salle tant que la caisse est fermee : ouverture de table,
     * saisie, envoi en preparation, service, addition. Les annulations restent
     * possibles pour que le gerant remette la salle en ordre.
     */
    private void caisseOuverte() {
        if (org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()
                .getPrincipal() instanceof UtilisateurPrincipal utilisateur) {
            sessionsTravail.exigerCaisseOuverte(utilisateur);
        }
    }
}
