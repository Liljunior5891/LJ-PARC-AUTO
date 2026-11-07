package tg.parcauto.api.pointdevente.depense;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.Autorisations;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.time.LocalDate;
import java.util.List;

/** Depenses du point de vente : saisie par le gerant ou l'administrateur ; categories reservees a l'administrateur. */
@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@RequestMapping("/api/pdv/depenses")
@PreAuthorize(Autorisations.PDV_GESTION)
@RequiredArgsConstructor
@Tag(name = "Point de vente - depenses")
public class DepensePdvController {

    private final DepensePdvService service;

    // ------------------------------------------------------------- depenses

    @GetMapping
    @Operation(summary = "Depenses de la periode (le mois en cours par defaut), les plus recentes d'abord")
    public Page<DepensePdvService.Reponse> rechercher(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin,
            @RequestParam(required = false) Long categorieId,
            @RequestParam(required = false) ModeSortie mode,
            @RequestParam(required = false) DepensePdv.Statut statut,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return service.rechercher(debut, fin, categorieId, mode, statut, q, page, size);
    }

    @GetMapping("/synthese")
    @Operation(summary = "Total des depenses valides de la periode, par categorie et par moyen de paiement")
    public DepensePdvService.Synthese synthese(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return service.synthese(debut, fin);
    }

    @GetMapping("/{id}")
    public DepensePdvService.Reponse parId(@PathVariable Long id) {
        return service.parId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistre une depense ; payee depuis la caisse, elle cree une sortie d'especes")
    public DepensePdvService.Reponse creer(@Valid @RequestBody DepensePdvService.Requete requete,
                                           @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.creer(requete, principal);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Corrige une depense (hors montant et moyen de paiement)")
    public DepensePdvService.Reponse modifier(@PathVariable Long id,
                                              @Valid @RequestBody DepensePdvService.Modification requete) {
        return service.modifier(id, requete);
    }

    @PostMapping("/{id}/annulation")
    @Operation(summary = "Annule une depense ; payee depuis la caisse, la sortie est retiree de la session")
    public DepensePdvService.Reponse annuler(@PathVariable Long id,
                                             @Valid @RequestBody DepensePdvService.MotifAnnulation motif,
                                             @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.annuler(id, motif.motif(), principal);
    }

    @PostMapping(value = "/{id}/justificatif", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Joint ou remplace le justificatif (photo ou PDF, 15 Mo au plus)")
    public DepensePdvService.Reponse joindre(@PathVariable Long id, @RequestPart("fichier") MultipartFile fichier) {
        return service.joindre(id, fichier);
    }

    @GetMapping("/{id}/justificatif")
    @Operation(summary = "Telecharge le justificatif")
    public ResponseEntity<Resource> justificatif(@PathVariable Long id) {
        DepensePdvService.Reponse d = service.parId(id);
        Resource fichier = service.justificatif(id);
        String nom = d.justificatifNom() == null ? "justificatif-" + d.numero() : d.justificatifNom();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nom.replace("\"", "") + "\"")
                .contentType(MediaTypeDuNom.de(nom))
                .body(fichier);
    }

    @DeleteMapping("/{id}/justificatif")
    @Operation(summary = "Retire le justificatif")
    public DepensePdvService.Reponse retirerJustificatif(@PathVariable Long id) {
        return service.retirerJustificatif(id);
    }

    // ----------------------------------------------------------- categories

    @GetMapping("/categories")
    @Operation(summary = "Categories de depense (liste de base creee a la premiere consultation)")
    public List<DepensePdvService.CategorieReponse> categories() {
        return service.categories();
    }

    @PostMapping("/categories")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @ResponseStatus(HttpStatus.CREATED)
    public DepensePdvService.CategorieReponse creerCategorie(@Valid @RequestBody DepensePdvService.CategorieRequete r) {
        return service.creerCategorie(r);
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    public DepensePdvService.CategorieReponse modifierCategorie(@PathVariable Long id,
                                                                @Valid @RequestBody DepensePdvService.CategorieRequete r) {
        return service.modifierCategorie(id, r);
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerCategorie(@PathVariable Long id) {
        service.supprimerCategorie(id);
    }

    // ---------------------------------------------------------- recurrentes

    @GetMapping("/recurrentes")
    @Operation(summary = "Depenses recurrentes (loyer, salaires, abonnements)")
    public List<DepensePdvService.RecurrenteReponse> recurrentes() {
        return service.recurrentes();
    }

    @GetMapping("/a-payer")
    @Operation(summary = "Mois dus et non payes des depenses recurrentes")
    public List<DepensePdvService.APayer> aPayer() {
        return service.aPayer();
    }

    @PostMapping("/recurrentes")
    @ResponseStatus(HttpStatus.CREATED)
    public DepensePdvService.RecurrenteReponse creerRecurrente(@Valid @RequestBody DepensePdvService.RecurrenteRequete r) {
        return service.creerRecurrente(r);
    }

    @PutMapping("/recurrentes/{id}")
    public DepensePdvService.RecurrenteReponse modifierRecurrente(@PathVariable Long id,
                                                                  @Valid @RequestBody DepensePdvService.RecurrenteRequete r) {
        return service.modifierRecurrente(id, r);
    }

    @DeleteMapping("/recurrentes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimerRecurrente(@PathVariable Long id) {
        service.supprimerRecurrente(id);
    }

    /** Type du justificatif d'apres son extension, pour l'afficher dans le navigateur. */
    private static final class MediaTypeDuNom {
        static MediaType de(String nom) {
            String n = nom.toLowerCase(java.util.Locale.ROOT);
            if (n.endsWith(".pdf")) {
                return MediaType.APPLICATION_PDF;
            }
            if (n.endsWith(".png")) {
                return MediaType.IMAGE_PNG;
            }
            if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
                return MediaType.IMAGE_JPEG;
            }
            if (n.endsWith(".webp")) {
                return MediaType.parseMediaType("image/webp");
            }
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
