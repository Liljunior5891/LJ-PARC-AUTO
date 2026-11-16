package tg.parcauto.api.pointdevente.vente;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import tg.parcauto.api.security.Autorisations;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@RequestMapping("/api/pdv/ventes")
@RequiredArgsConstructor
@Tag(name = "Point de vente - ventes")
public class VenteController {

    private final VenteService service;

    @PostMapping
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Encaisse une commande sur la session de caisse ouverte de l'utilisateur")
    public VenteDto.Ticket encaisser(@Valid @RequestBody VenteDto.Encaissement demande,
                                     @AuthenticationPrincipal UtilisateurPrincipal principal) {
        try {
            return service.encaisser(demande, principal);
        } catch (org.springframework.dao.DataIntegrityViolationException
                 | org.springframework.dao.ConcurrencyFailureException e) {
            // Deux envois de la meme vente en meme temps : le second bute sur la cle unique ou sur
            // la version du stock ; il recoit le ticket de la vente enregistree par le premier.
            String cle = demande.cleHorsLigne() == null ? null : demande.cleHorsLigne().trim();
            if (cle != null && !cle.isEmpty()) {
                VenteDto.Ticket deja = attendreVente(cle);
                if (deja != null) {
                    return deja;
                }
            }
            throw e;
        }
    }

    /** Le premier envoi peut etre en cours de validation : quelques courtes attentes, une seconde au plus. */
    private VenteDto.Ticket attendreVente(String cle) {
        for (int essai = 0; essai < 5; essai++) {
            VenteDto.Ticket deja = service.ticketParCle(cle);
            if (deja != null) {
                return deja;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException interrompu) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /** Sans bornes : les dernieres 24 heures. Avec une session : tous ses tickets. */
    @GetMapping
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Commandes d'une periode ou d'une session de caisse")
    public Page<VenteDto.Resume> lister(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fin,
            @RequestParam(required = false) Long sessionId,
            /** Canal de vente : vrai = en ligne (Marketplace), faux = comptoir ; absent = tous. */
            @RequestParam(required = false) Boolean enLigne,
            /** Avec une session : numero de commande, nom ou telephone du client. */
            @RequestParam(required = false) String q,
            @PageableDefault(size = 30) Pageable pageable,
            @AuthenticationPrincipal UtilisateurPrincipal principal) {
        Instant finRetenue = fin == null ? Instant.now().plusSeconds(1) : fin;
        Instant debutRetenu = debut == null ? finRetenue.minus(1, ChronoUnit.DAYS) : debut;
        return service.lister(debutRetenu, finRetenue, sessionId, enLigne, q, principal, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Commande complete, pour consultation ou reimpression du recu")
    public VenteDto.Ticket ticket(@PathVariable Long id, @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.ticket(id, principal);
    }

    @PostMapping("/{id}/annulation")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Annule une commande et remet ses articles en stock")
    public VenteDto.Ticket annuler(@PathVariable Long id, @Valid @RequestBody VenteDto.Annulation annulation,
                                   @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.annuler(id, annulation.motif(), principal);
    }
}
