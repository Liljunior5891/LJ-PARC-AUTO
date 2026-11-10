package tg.parcauto.api.pointdevente.materiaux;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
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
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.parc.document.DocumentDto;
import tg.parcauto.api.parc.document.DocumentService;
import tg.parcauto.api.parc.document.ProprietaireDocument;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.pointdevente.vente.VenteDto;
import tg.parcauto.api.pointdevente.vente.VenteService;
import tg.parcauto.api.security.Autorisations;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.time.LocalDate;
import java.util.List;

/**
 * Point de vente « Quincaillerie & materiaux » : reglages, comptes clients et
 * reglements, devis, conditionnements et facture A4.
 * <p>
 * Les reglages ne se modifient que par l'administrateur ; le plafond de
 * credit d'un client, par un gerant ou l'administrateur.
 */
@RestController
@ModuleRequis(value = ModuleApplicatif.POINT_DE_VENTE, variante = ReglageMateriauxService.MATERIAUX)
@RequestMapping("/api/pdv")
@RequiredArgsConstructor
@Tag(name = "Point de vente - quincaillerie et materiaux")
public class MateriauxController {

    private final ReglageMateriauxService reglages;
    private final CompteClientService comptes;
    private final DevisService devis;
    private final ConditionnementService conditionnements;
    private final DocumentMateriauxPdf documents;
    private final VenteService ventes;
    private final DocumentService piecesClients;
    private final LivraisonClientService livraisons;

    // ------------------------------------------------------------- reglages

    @GetMapping("/materiaux/reglages")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Reglages de la quincaillerie (credit, devis, facture, conditionnements)")
    public MateriauxDto.Reglages reglages() {
        return reglages.lire();
    }

    @PutMapping("/materiaux/reglages")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Modifie les reglages de la quincaillerie (administrateur)")
    public MateriauxDto.Reglages modifierReglages(@Valid @RequestBody MateriauxDto.Reglages r) {
        return reglages.modifier(r);
    }

    // -------------------------------------------------------------- clients

    @GetMapping("/clients")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Clients, avec leur encours ; debiteurs=true : ceux qui doivent, les plus gros d'abord")
    public Page<MateriauxDto.ClientReponse> clients(@RequestParam(required = false) String q,
                                                    @RequestParam(required = false) Boolean actif,
                                                    @RequestParam(required = false) ClientPdv.Type type,
                                                    @RequestParam(required = false) Boolean debiteurs,
                                                    @PageableDefault(size = 30) Pageable pageable) {
        return comptes.rechercher(q, actif, type, debiteurs, pageable);
    }

    @GetMapping("/clients/par-telephone")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Clients ayant ce numero de telephone, quelle que soit son ecriture")
    public List<MateriauxDto.ClientReponse> clientsParTelephone(@RequestParam String telephone) {
        return comptes.parTelephone(telephone);
    }

    @GetMapping("/clients/{id}")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Fiche d'un client et son encours")
    public MateriauxDto.ClientReponse client(@PathVariable Long id) {
        return comptes.parId(id);
    }

    @PostMapping("/clients")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cree un client ; le plafond et le delai ne sont retenus que d'un gerant")
    public MateriauxDto.ClientReponse creerClient(@Valid @RequestBody MateriauxDto.ClientRequete r,
                                                  @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return comptes.creer(r, principal);
    }

    @PutMapping("/clients/{id}")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Modifie un client, son plafond de credit et son delai de paiement")
    public MateriauxDto.ClientReponse modifierClient(@PathVariable Long id,
                                                     @Valid @RequestBody MateriauxDto.ClientRequete r) {
        return comptes.modifier(id, r);
    }

    @DeleteMapping("/clients/{id}")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Archive un client ; ses ventes et ses reglements restent consultables")
    public void archiverClient(@PathVariable Long id) {
        comptes.archiver(id);
    }

    // ------------------------------------------------- livraison en plusieurs fois

    @GetMapping("/livraisons/reste")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Ventes dont il reste de la marchandise a remettre au client")
    public Page<MateriauxDto.VenteALivrer> resteALivrer(@RequestParam(required = false) String q,
                                                        @RequestParam(required = false) Long clientId,
                                                        @PageableDefault(size = 30) Pageable pageable,
                                                        @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return livraisons.resteALivrer(q, clientId, principal, pageable);
    }

    @GetMapping("/livraisons/historique")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Commandes qui ont un bon de livraison ou un solde, meme entierement remises")
    public Page<MateriauxDto.VenteALivrer> historiqueLivraisons(@RequestParam(required = false) String q,
                                                                @RequestParam(required = false) Long clientId,
                                                                @PageableDefault(size = 30) Pageable pageable,
                                                                @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return livraisons.historique(q, clientId, principal, pageable);
    }

    @PostMapping("/ventes/{id}/soldes")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Le client renonce au reste : la vente est corrigee, le stock libere et le surplus rembourse")
    public MateriauxDto.SoldeReponse solder(@PathVariable Long id, @Valid @RequestBody MateriauxDto.SoldeRequete r,
                                            @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return livraisons.solder(id, r, principal);
    }

    @GetMapping("/ventes/{id}/soldes")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Soldes du reste d'une vente")
    public List<MateriauxDto.SoldeReponse> soldesDeLaVente(@PathVariable Long id,
                                                           @AuthenticationPrincipal UtilisateurPrincipal principal) {
        livraisons.exigerVente(id, principal);
        return livraisons.soldesDeLaVente(id);
    }

    @PostMapping("/soldes/{id}/annulation")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Annule un solde tant que la session de son remboursement est ouverte")
    public MateriauxDto.SoldeReponse annulerSolde(@PathVariable Long id, @Valid @RequestBody MateriauxDto.AnnulationBon r,
                                                  @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return livraisons.annulerSolde(id, r.motif(), principal);
    }

    @GetMapping("/soldes/{id}/pdf")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Avoir au format PDF (A4), a signer par le client")
    public ResponseEntity<byte[]> soldePdf(@PathVariable Long id, @AuthenticationPrincipal UtilisateurPrincipal principal) {
        livraisons.exigerSolde(id, principal);
        return pdf(documents.avoir(id), documents.nomAvoir(id));
    }

    @PostMapping("/ventes/{id}/livraisons")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Remet au client tout ou partie de ce qui reste : le stock sort, un bon de livraison est emis")
    public MateriauxDto.BonReponse remettre(@PathVariable Long id, @Valid @RequestBody MateriauxDto.RemiseRequete r,
                                            @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return livraisons.remettre(id, r, principal);
    }

    @GetMapping("/ventes/{id}/livraisons")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Bons de livraison d'une vente")
    public List<MateriauxDto.BonReponse> bonsDeLaVente(@PathVariable Long id,
                                                       @AuthenticationPrincipal UtilisateurPrincipal principal) {
        livraisons.exigerVente(id, principal);
        return livraisons.bonsDeLaVente(id);
    }

    @PostMapping("/livraisons/{id}/annulation")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Annule une remise saisie par erreur : la marchandise revient en stock et redevient a livrer")
    public MateriauxDto.BonReponse annulerBon(@PathVariable Long id, @Valid @RequestBody MateriauxDto.AnnulationBon r,
                                              @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return livraisons.annuler(id, r.motif(), principal);
    }

    @GetMapping("/livraisons/{id}/pdf")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Bon de livraison au format PDF (A4), a signer par le client")
    public ResponseEntity<byte[]> bonPdf(@PathVariable Long id, @AuthenticationPrincipal UtilisateurPrincipal principal) {
        livraisons.exigerBon(id, principal);
        return pdf(documents.bonLivraison(id), documents.nomBonLivraison(id));
    }

    // ------------------------------------------- pieces justificatives des clients

    @GetMapping("/clients/{id}/documents")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Pieces justificatives d'un client")
    public Page<DocumentDto.Reponse> documentsClient(@PathVariable Long id,
                                                     @PageableDefault(size = 50) Pageable pageable) {
        return piecesClients.rechercher(ProprietaireDocument.CLIENT_PDV, id, null, pageable);
    }

    @PostMapping(value = "/clients/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajoute une piece justificative a un client")
    public DocumentDto.Reponse ajouterDocumentClient(@PathVariable Long id,
                                                     @Valid @RequestPart("document") DocumentDto.Requete requete,
                                                     @RequestPart(value = "fichier", required = false) MultipartFile fichier) {
        // Le client vise est celui de l'adresse, quoi que dise la requete.
        return piecesClients.creer(new DocumentDto.Requete(ProprietaireDocument.CLIENT_PDV, id, requete.type(),
                requete.libelle(), requete.numero(), requete.emetteur(), requete.dateEmission(),
                requete.dateExpiration(), requete.cout(), requete.observations()), fichier);
    }

    @GetMapping("/clients/documents/{documentId}/fichier")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Telecharge la piece justificative d'un client")
    public ResponseEntity<Resource> telechargerDocumentClient(@PathVariable Long documentId) {
        DocumentDto.Reponse meta = piecesClients.parId(documentId);
        // Ce point d'acces ne sert que les pieces des clients du point de vente, pas celles du parc.
        if (meta.proprietaireType() != ProprietaireDocument.CLIENT_PDV) {
            throw ResourceNotFoundException.of("Document", documentId);
        }
        String nom = meta.fichierNom() == null ? "document-" + documentId : meta.fichierNom();
        return tg.parcauto.api.common.FichierServi.reponse(piecesClients.telecharger(documentId), nom, false);
    }

    @GetMapping("/clients/{id}/compte")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Compte d'un client : ventes dues et reglements")
    public MateriauxDto.Compte compte(@PathVariable Long id) {
        return comptes.compte(id);
    }

    @GetMapping("/clients/{id}/ventes")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Achats d'un client, les plus recents d'abord")
    public Page<VenteDto.Resume> ventesDuClient(@PathVariable Long id, @PageableDefault(size = 20) Pageable pageable) {
        return ventes.duClient(id, pageable);
    }

    @PostMapping("/clients/{id}/reglements")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Encaisse un versement du client sur ses ventes a credit")
    public MateriauxDto.RecuReglement regler(@PathVariable Long id,
                                             @Valid @RequestBody MateriauxDto.ReglementRequete r,
                                             @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return comptes.regler(id, r, principal);
    }

    @GetMapping("/reglements/{numero}")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Recu d'un versement, pour reimpression")
    public MateriauxDto.RecuReglement recu(@PathVariable String numero, @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return comptes.recu(numero, principal);
    }

    @PostMapping("/reglements/{numero}/annulation")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Annule un versement saisi par erreur, tant que sa session est ouverte")
    public MateriauxDto.RecuReglement annulerReglement(@PathVariable String numero,
                                                       @Valid @RequestBody MateriauxDto.Annulation a,
                                                       @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return comptes.annuler(numero, a.motif(), principal);
    }

    // ---------------------------------------------------------------- devis

    @GetMapping("/devis")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Devis, filtres par statut, client ou texte")
    public Page<MateriauxDto.DevisReponse> listeDevis(@RequestParam(required = false) String q,
                                                     @RequestParam(required = false) Devis.Statut statut,
                                                     @RequestParam(required = false) Long clientId,
                                                     @PageableDefault(size = 30) Pageable pageable) {
        return devis.rechercher(q, statut, clientId, pageable);
    }

    @GetMapping("/devis/{id}")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Devis complet")
    public MateriauxDto.DevisReponse unDevis(@PathVariable Long id) {
        return devis.detail(id);
    }

    @PostMapping("/devis")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Etablit un devis aux prix du jour")
    public MateriauxDto.DevisReponse creerDevis(@Valid @RequestBody MateriauxDto.DevisRequete r,
                                                @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return devis.creer(r, principal);
    }

    @PutMapping("/devis/{id}")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Modifie un devis en cours")
    public MateriauxDto.DevisReponse modifierDevis(@PathVariable Long id,
                                                   @Valid @RequestBody MateriauxDto.DevisRequete r) {
        return devis.modifier(id, r);
    }

    @PostMapping("/devis/{id}/prolongation")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Repousse la date de validite d'un devis")
    public MateriauxDto.DevisReponse prolonger(@PathVariable Long id,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jusqua) {
        return devis.prolonger(id, jusqua);
    }

    @PostMapping("/devis/{id}/annulation")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Annule un devis en cours")
    public MateriauxDto.DevisReponse annulerDevis(@PathVariable Long id) {
        return devis.annuler(id);
    }

    @PostMapping("/devis/{id}/encaissement")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Encaisse un devis accepte : il devient une vente a ses prix")
    public VenteDto.Ticket encaisserDevis(@PathVariable Long id,
                                          @Valid @RequestBody MateriauxDto.EncaissementDevis e,
                                          @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return devis.encaisser(id, e, principal);
    }

    @GetMapping("/devis/{id}/pdf")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Devis au format PDF (A4)")
    public ResponseEntity<byte[]> devisPdf(@PathVariable Long id) {
        return pdf(documents.devis(id), documents.nomDevis(id));
    }

    // ----------------------------------------------------- conditionnements

    @GetMapping("/conditionnements")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Conditionnements vendables, charges par la caisse")
    public List<MateriauxDto.ConditionnementReponse> conditionnementsVendables() {
        return conditionnements.vendables();
    }

    @GetMapping("/articles/{id}/conditionnements")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Conditionnements d'un produit")
    public List<MateriauxDto.ConditionnementReponse> conditionnementsDuProduit(@PathVariable Long id) {
        return conditionnements.duProduit(id);
    }

    @PutMapping("/articles/{id}/conditionnements")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Enregistre les conditionnements d'un produit ; les absents sont retires de la vente")
    public List<MateriauxDto.ConditionnementReponse> enregistrerConditionnements(
            @PathVariable Long id, @Valid @RequestBody List<MateriauxDto.@Valid ConditionnementRequete> demandes) {
        return conditionnements.enregistrer(id, demandes);
    }

    // -------------------------------------------------------------- facture

    @GetMapping("/ventes/{id}/facture-pdf")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Facture A4 d'une vente")
    public ResponseEntity<byte[]> facture(@PathVariable Long id, @AuthenticationPrincipal UtilisateurPrincipal principal) {
        livraisons.exigerVente(id, principal);
        return pdf(documents.facture(id), documents.nomFacture(id));
    }

    private static ResponseEntity<byte[]> pdf(byte[] contenu, String nom) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(nom).build().toString())
                .body(contenu);
    }
}
