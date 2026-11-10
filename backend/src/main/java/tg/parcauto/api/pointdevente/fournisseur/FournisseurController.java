package tg.parcauto.api.pointdevente.fournisseur;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.parc.document.DocumentDto;
import tg.parcauto.api.parc.document.DocumentService;
import tg.parcauto.api.parc.document.ProprietaireDocument;

/** Fournisseurs du point de vente, leurs dettes et leurs reglements. */
@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@RequestMapping("/api/pdv/fournisseurs")
@PreAuthorize(Autorisations.PDV_GESTION)
@RequiredArgsConstructor
@Tag(name = "Point de vente - fournisseurs")
public class FournisseurController {

    private final FournisseurService service;
    private final DocumentService pieces;

    @GetMapping
    @Operation(summary = "Fournisseurs, avec ce qu'on leur doit")
    public Page<FournisseurService.Reponse> rechercher(@RequestParam(required = false) String q,
                                                       @RequestParam(required = false) Boolean actif,
                                                       @RequestParam(defaultValue = "false") boolean dettes,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "30") int size) {
        return service.rechercher(q, actif, dettes, page, size);
    }

    @GetMapping("/actifs")
    @Operation(summary = "Fournisseurs actifs, pour les listes de choix")
    public List<FournisseurService.Reponse> actifs() {
        return service.actifs();
    }

    @GetMapping("/totaux")
    @Operation(summary = "Total du aux fournisseurs et part en retard")
    public FournisseurService.Totaux totaux() {
        return service.totaux();
    }

    @GetMapping("/{id}")
    public FournisseurService.Reponse parId(@PathVariable Long id) {
        return service.parId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Nouveau fournisseur")
    public FournisseurService.Reponse creer(@Valid @RequestBody FournisseurService.Requete requete) {
        return service.creer(requete);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifie la fiche d'un fournisseur")
    public FournisseurService.Reponse modifier(@PathVariable Long id,
                                               @Valid @RequestBody FournisseurService.Requete requete) {
        return service.modifier(id, requete);
    }

    @PostMapping("/{id}/archivage")
    @Operation(summary = "Archive un fournisseur (historique conserve)")
    public FournisseurService.Reponse archiver(@PathVariable Long id) {
        return service.archiver(id, false);
    }

    @PostMapping("/{id}/reactivation")
    @Operation(summary = "Reactive un fournisseur archive")
    public FournisseurService.Reponse reactiver(@PathVariable Long id) {
        return service.archiver(id, true);
    }

    @GetMapping("/{id}/compte")
    @Operation(summary = "Compte du fournisseur : commandes a payer, reglements, historique")
    public FournisseurService.Compte compte(@PathVariable Long id) {
        return service.compte(id);
    }

    @PostMapping("/{id}/reglements")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Paie un fournisseur ; depuis la caisse, cree une sortie d'especes")
    public FournisseurService.Recu regler(@PathVariable Long id,
                                          @Valid @RequestBody FournisseurService.ReglementRequete requete,
                                          @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.regler(id, requete, principal);
    }

    @PostMapping("/reglements/{numero}/annulation")
    @Operation(summary = "Annule un reglement fournisseur")
    public FournisseurService.Recu annuler(@PathVariable String numero,
                                           @Valid @RequestBody FournisseurService.MotifAnnulation motif,
                                           @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return service.annuler(numero, motif.motif(), principal);
    }

    // ------------------------------------------------------------ pieces

    @GetMapping("/{id}/documents")
    @Operation(summary = "Pieces jointes d'un fournisseur (RCCM, NIF, contrat...)")
    public Page<DocumentDto.Reponse> documents(@PathVariable Long id, @PageableDefault(size = 50) Pageable pageable) {
        return pieces.rechercher(ProprietaireDocument.FOURNISSEUR_PDV, id, null, pageable);
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajoute une piece jointe a un fournisseur")
    public DocumentDto.Reponse ajouterDocument(@PathVariable Long id,
                                               @Valid @RequestPart("document") DocumentDto.Requete requete,
                                               @RequestPart(value = "fichier", required = false) MultipartFile fichier) {
        // Le fournisseur vise est celui de l'adresse, quoi que dise la requete.
        return pieces.creer(new DocumentDto.Requete(ProprietaireDocument.FOURNISSEUR_PDV, id, requete.type(),
                requete.libelle(), requete.numero(), requete.emetteur(), requete.dateEmission(),
                requete.dateExpiration(), requete.cout(), requete.observations()), fichier);
    }

    @GetMapping("/documents/{documentId}/fichier")
    @Operation(summary = "Telecharge la piece jointe d'un fournisseur")
    public ResponseEntity<Resource> telechargerDocument(@PathVariable Long documentId) {
        DocumentDto.Reponse meta = pieces.parId(documentId);
        if (meta.proprietaireType() != ProprietaireDocument.FOURNISSEUR_PDV) {
            throw ResourceNotFoundException.of("Document", documentId);
        }
        String nom = meta.fichierNom() == null ? "document-" + documentId : meta.fichierNom();
        return tg.parcauto.api.common.FichierServi.reponse(pieces.telecharger(documentId), nom, false);
    }

    @PostMapping("/documents/{documentId}/archivage")
    @Operation(summary = "Retire une piece jointe d'un fournisseur")
    public void archiverDocument(@PathVariable Long documentId) {
        DocumentDto.Reponse meta = pieces.parId(documentId);
        if (meta.proprietaireType() != ProprietaireDocument.FOURNISSEUR_PDV) {
            throw ResourceNotFoundException.of("Document", documentId);
        }
        pieces.archiver(documentId);
    }
}
