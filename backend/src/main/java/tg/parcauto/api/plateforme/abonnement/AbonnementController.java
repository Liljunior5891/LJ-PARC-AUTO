package tg.parcauto.api.plateforme.abonnement;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;
import tg.parcauto.api.security.Autorisations;

import java.util.List;

/**
 * Abonnement vu par l'entreprise : etat, formules proposees, devis, declaration
 * des paiements et factures. Reste ouvert en lecture seule (voir {@link LectureSeuleInterceptor}).
 */
@RestController
@RequestMapping("/api/abonnement")
@RequiredArgsConstructor
@Tag(name = "Abonnement")
public class AbonnementController {

    private final AbonnementService service;
    private final tg.parcauto.api.plateforme.abonnement.paygate.PaiementEnLigneService enLigne;

    @GetMapping("/etat")
    @PreAuthorize("isAuthenticated() and !hasRole('SUPER_ADMIN')")
    @Operation(summary = "Etat resume de l'abonnement, pour le bandeau de rappel")
    public AbonnementService.EtatCourt etat() {
        return service.etatCourt(ContexteEntreprise.exiger());
    }

    @GetMapping
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Abonnement en cours, modules payes et consommation des limites")
    public AbonnementService.AbonnementVue abonnement() {
        return service.vueAbonnement(ContexteEntreprise.exiger());
    }

    @GetMapping("/offre")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Formules proposees avec leurs prix par module, et remises")
    public AbonnementService.Offre offre() {
        return service.offre();
    }

    @PostMapping("/devis")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Montant a payer pour une formule, des modules et une periodicite")
    public AbonnementService.Devis devis(@Valid @RequestBody AbonnementService.DemandeDevis demande) {
        return service.devis(demande);
    }

    @GetMapping("/paiements")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Paiements declares et factures de l'entreprise")
    public List<AbonnementService.PaiementVue> paiements() {
        return service.paiementsDe(ContexteEntreprise.exiger());
    }

    @PostMapping("/paiements")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Declare un paiement, a valider par l'exploitant de la plateforme")
    public AbonnementService.PaiementVue declarer(@Valid @RequestBody AbonnementService.Declaration declaration) {
        return service.declarer(ContexteEntreprise.exiger(), declaration);
    }

    @PostMapping("/paiements/en-ligne")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Paie l'abonnement en ligne (Flooz, T-Money) : la demande arrive sur le telephone")
    public AbonnementService.PaiementVue payerEnLigne(@Valid @RequestBody AbonnementService.DemandeEnLigne demande) {
        return enLigne.payer(tg.parcauto.api.plateforme.tenant.ContexteEntreprise.exiger(), demande);
    }

    @PostMapping("/paiements/{id}/verification")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Verifie aupres de PayGate l'etat d'un paiement en ligne")
    public AbonnementService.PaiementVue verifierEnLigne(@PathVariable Long id) {
        return enLigne.verifier(tg.parcauto.api.plateforme.tenant.ContexteEntreprise.exiger(), id);
    }

    @DeleteMapping("/paiements/{id}")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Annule une declaration encore en attente")
    public void annuler(@PathVariable Long id) {
        service.annulerDeclaration(ContexteEntreprise.exiger(), id);
    }

    @PostMapping(value = "/paiements/{id}/preuve", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Joint la preuve du paiement (capture, recu, bordereau)")
    public AbonnementService.PaiementVue joindre(@PathVariable Long id, @RequestPart("fichier") MultipartFile fichier) {
        return service.joindrePreuve(ContexteEntreprise.exiger(), id, fichier);
    }

    @GetMapping("/paiements/{id}/preuve")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Telecharge la preuve du paiement")
    public ResponseEntity<Resource> preuve(@PathVariable Long id) {
        return fichier(service, service.paiementAvecPreuve(ContexteEntreprise.exiger(), id));
    }

    static ResponseEntity<Resource> fichier(AbonnementService service, PaiementAbonnement p) {
        String nom = p.getPreuveNom() == null ? "preuve" : p.getPreuveNom().replaceAll("[\"\\r\\n\\\\]", "");
        // Type tire de l'extension : un fichier deguise en HTML part en telechargement, sans s'executer.
        String type = AbonnementService.typePreuve(nom);
        return ResponseEntity.ok()
                .contentType(type == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(type))
                .header(HttpHeaders.CONTENT_DISPOSITION, (type == null ? "attachment" : "inline")
                        + "; filename=\"" + nom + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(service.preuve(p));
    }
}
