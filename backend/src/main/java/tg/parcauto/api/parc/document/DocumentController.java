package tg.parcauto.api.parc.document;

import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import tg.parcauto.api.security.Autorisations;


// Donnees de gestion (clients, contrats, personnel, pieces) : pas pour les chauffeurs.
@org.springframework.security.access.prepost.PreAuthorize(tg.parcauto.api.security.Autorisations.GESTION)
@RestController
@ModuleRequis(ModuleApplicatif.GESTION_PARC)
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "Documents")
public class DocumentController {


    private final DocumentService service;

    /**
     * Pieces des clients et fournisseurs du point de vente : gerees depuis le point de
     * vente, jamais par ce point du parc (identite des clients, contrats fournisseurs).
     */
    private static final java.util.Set<ProprietaireDocument> TYPES_PDV =
            java.util.EnumSet.of(ProprietaireDocument.CLIENT_PDV, ProprietaireDocument.FOURNISSEUR_PDV);

    private static void exigerTypeParc(ProprietaireDocument type) {
        if (type != null && TYPES_PDV.contains(type)) {
            throw new tg.parcauto.api.common.ResourceNotFoundException("Document introuvable");
        }
    }

    private DocumentDto.Reponse documentDuParc(Long id) {
        DocumentDto.Reponse d = service.parId(id);
        exigerTypeParc(d.proprietaireType());
        return d;
    }

    @GetMapping
    @Operation(summary = "Liste des documents, filtrable par proprietaire et par type")
    public Page<DocumentDto.Reponse> lister(@RequestParam(required = false) ProprietaireDocument proprietaireType,
                                            @RequestParam(required = false) Long proprietaireId,
                                            @RequestParam(required = false) TypeDocument type,
                                            @PageableDefault(size = 20, sort = "dateExpiration",
                                                    direction = Sort.Direction.ASC) Pageable pageable) {
        exigerTypeParc(proprietaireType);
        return service.rechercher(proprietaireType, proprietaireId, type, pageable);
    }

    @GetMapping("/alertes/expiration")
    @Operation(summary = "Documents expires ou expirant prochainement")
    public List<DocumentDto.Reponse> alertes(@RequestParam(defaultValue = "30") int jours) {
        return service.aExpirer(Math.max(0, Math.min(jours, 3650))).stream()
                .filter(d -> !TYPES_PDV.contains(d.proprietaireType()))
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail d'un document")
    public DocumentDto.Reponse detail(@PathVariable Long id) {
        return documentDuParc(id);
    }

    @GetMapping("/{id}/fichier")
    @Operation(summary = "Telecharge le justificatif attache")
    public ResponseEntity<Resource> telecharger(@PathVariable Long id) {
        DocumentDto.Reponse meta = documentDuParc(id);
        Resource resource = service.telecharger(id);
        String nom = meta.fichierNom() == null ? "document-" + id : meta.fichierNom();
        return tg.parcauto.api.common.FichierServi.reponse(resource, nom, false);
    }

    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @PreAuthorize(Autorisations.GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistre un document avec justificatif optionnel")
    public DocumentDto.Reponse creer(@Valid @RequestPart("document") DocumentDto.Requete requete,
                                     @RequestPart(value = "fichier", required = false) MultipartFile fichier) {
        exigerTypeParc(requete.proprietaireType());
        return service.creer(requete, fichier);
    }

    @PutMapping(value = "/{id}", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @PreAuthorize(Autorisations.GESTION)
    @Operation(summary = "Met a jour un document")
    public DocumentDto.Reponse modifier(@PathVariable Long id,
                                        @Valid @RequestPart("document") DocumentDto.Requete requete,
                                        @RequestPart(value = "fichier", required = false) MultipartFile fichier) {
        documentDuParc(id);
        exigerTypeParc(requete.proprietaireType());
        return service.modifier(id, requete, fichier);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Autorisations.GESTION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive un document")
    public void archiver(@PathVariable Long id) {
        documentDuParc(id);
        service.archiver(id);
    }
}
