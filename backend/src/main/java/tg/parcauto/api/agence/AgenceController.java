package tg.parcauto.api.agence;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.pointdevente.personnel.PersonnelService;
import tg.parcauto.api.security.Autorisations;

import java.util.List;

/** Agences, personnel rattache, reglages et droits du module Agences multiservices. */
@RestController
@ModuleRequis(ModuleApplicatif.AGENCES)
@RequestMapping("/api/agences")
@RequiredArgsConstructor
@Tag(name = "Agences - agences et reglages")
public class AgenceController {

    private final AgenceService service;
    private final ReglageAgenceService reglages;

    @GetMapping("/moi")
    @Operation(summary = "Droits de l'utilisateur connecte et agences qu'il voit")
    public ReglageAgenceService.Moi moi() {
        return reglages.moi();
    }

    @GetMapping
    @Operation(summary = "Agences visibles : toutes pour l'administrateur, celles ou l'on est rattache sinon")
    public List<AgenceService.Reponse> lister() {
        return service.lister();
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Une agence et son personnel")
    public AgenceService.Reponse detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @GetMapping("/personnel")
    @PreAuthorize("@droitsAgence.a('AGENCES_GERER')")
    @Operation(summary = "Superviseurs et caissiers d'agence de l'entreprise, a rattacher aux agences")
    public List<PersonnelService.Membre> personnel() {
        return service.personnelAgence();
    }

    @PostMapping
    @PreAuthorize("@droitsAgence.a('AGENCES_GERER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cree une agence ; ses comptes (caisse, floats, commissions) sont crees d'office")
    public AgenceService.Reponse creer(@Valid @RequestBody AgenceService.Requete requete) {
        return service.creer(requete);
    }

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("@droitsAgence.a('AGENCES_GERER')")
    @Operation(summary = "Modifie une agence et son personnel")
    public AgenceService.Reponse modifier(@PathVariable Long id, @Valid @RequestBody AgenceService.Requete requete) {
        return service.modifier(id, requete);
    }

    // ------------------------------------------------------------- reglages

    @GetMapping("/reglages")
    @Operation(summary = "Reglages communs aux agences (journee, doublons, plafonds)")
    public ReglageAgenceService.Reglages lireReglages() {
        return reglages.lire();
    }

    @PutMapping("/reglages")
    @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")
    @Operation(summary = "Enregistre les reglages communs aux agences")
    public ReglageAgenceService.Reglages enregistrerReglages(@Valid @RequestBody ReglageAgenceService.Reglages r) {
        return reglages.enregistrer(r);
    }

    @GetMapping("/droits")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Droits du superviseur et du caissier d'agence")
    public ReglageAgenceService.Droits droits() {
        return reglages.droits();
    }

    @PutMapping("/droits")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Remplace les droits du superviseur et du caissier d'agence")
    public ReglageAgenceService.Droits enregistrerDroits(@RequestBody List<ReglageAgenceService.ProfilDroits> profils) {
        return reglages.enregistrerDroits(profils);
    }
}
