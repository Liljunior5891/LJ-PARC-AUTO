package tg.parcauto.api.pointdevente.lieu;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.pointdevente.materiaux.DocumentMateriauxPdf;
import tg.parcauto.api.pointdevente.materiaux.MateriauxDto;
import tg.parcauto.api.pointdevente.materiaux.ReglageMateriauxService;
import tg.parcauto.api.security.Autorisations;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.util.List;

/** Magasins, entrepots, bons de retrait et transferts de la quincaillerie. */
@RestController
@ModuleRequis(value = ModuleApplicatif.POINT_DE_VENTE, variante = ReglageMateriauxService.MATERIAUX)
@RequestMapping("/api/pdv")
@RequiredArgsConstructor
@Tag(name = "Point de vente - magasins et entrepots")
public class LieuController {

    private final LieuStockService lieux;
    private final RetraitService retraits;
    private final TransfertService transferts;
    private final DocumentMateriauxPdf documents;

    // ------------------------------------------------------------------- lieux

    @GetMapping("/lieux/etat")
    @PreAuthorize(Autorisations.PDV_LIEUX)
    @Operation(summary = "Le fonctionnement par lieu est-il actif, et quels lieux pour cet utilisateur")
    public LieuStockService.Etat etat(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return lieux.etat(principal);
    }

    @GetMapping("/lieux")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Magasins et entrepots, avec la quantite et la valeur de leur stock")
    public List<LieuStockService.Reponse> lister() {
        return lieux.lister();
    }

    @PostMapping("/lieux")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cree un lieu ; le premier entrepot active le fonctionnement par lieu")
    public LieuStockService.Reponse creer(@Valid @RequestBody LieuStockService.Requete r) {
        return lieux.creer(r);
    }

    @PutMapping("/lieux/{id}")
    @PreAuthorize(Autorisations.ADMINISTRATION)
    @Operation(summary = "Modifie un lieu ; il ne se desactive que vide")
    public LieuStockService.Reponse modifier(@PathVariable Long id, @Valid @RequestBody LieuStockService.Requete r) {
        return lieux.modifier(id, r);
    }

    @GetMapping("/lieux/disponibilites")
    @PreAuthorize(Autorisations.PDV_LIEUX)
    @Operation(summary = "Disponible de chaque lieu pour ces articles")
    public List<LieuStockService.Disponibilite> disponibilites(@RequestParam List<Long> articles,
                                                               @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return lieux.disponibilites(articles, principal);
    }

    @GetMapping("/lieux/produits")
    @PreAuthorize(Autorisations.PDV_MAGASIN)
    @Operation(summary = "Produits a demander en transfert, sans quantite ni prix")
    public List<LieuStockService.ProduitCourt> produits() {
        return lieux.produits();
    }

    @GetMapping("/lieux/caisse/{caisseId}")
    @PreAuthorize(Autorisations.PDV_LIEUX)
    @Operation(summary = "Magasin d'ou vend une caisse")
    public LieuStockService.LieuCourt lieuDeCaisse(@PathVariable Long caisseId) {
        return lieux.lieuDeCaisseId(caisseId);
    }

    @GetMapping("/lieux/{id}/stock")
    @PreAuthorize(Autorisations.PDV_MAGASIN)
    @Operation(summary = "Stock d'un lieu de l'utilisateur : des quantites, sans prix")
    public Page<LieuStockService.LigneStock> stock(@PathVariable Long id, @RequestParam(required = false) String q,
                                                   @PageableDefault(size = 50) Pageable pageable,
                                                   @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return lieux.stockDuLieu(id, q, pageable, principal);
    }

    @PutMapping("/lieux/{id}/stock/{articleId}/seuil")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Seuil d'alerte d'un article dans un lieu")
    public LieuStockService.LigneStock seuil(@PathVariable Long id, @PathVariable Long articleId,
                                             @Valid @RequestBody LieuStockService.Seuil r) {
        return lieux.modifierSeuil(id, articleId, r.seuilAlerte());
    }

    // ---------------------------------------------------------- bons de retrait

    @GetMapping("/retraits")
    @PreAuthorize(Autorisations.PDV_MAGASIN)
    @Operation(summary = "Bons de retrait des lieux de l'utilisateur, les plus anciens d'abord")
    public Page<RetraitService.Bon> retraits(@RequestParam(required = false) String q, @RequestParam(required = false) Long lieuId,
                                             @RequestParam(defaultValue = "false") boolean tous,
                                             @PageableDefault(size = 30) Pageable pageable,
                                             @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return retraits.rechercher(q, lieuId, tous, principal, pageable);
    }

    @GetMapping("/retraits/numero/{numero}")
    @PreAuthorize(Autorisations.PDV_MAGASIN)
    @Operation(summary = "Bon lu au scanner ou saisi au comptoir")
    public RetraitService.Bon parNumero(@PathVariable String numero, @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return retraits.parNumero(numero, principal);
    }

    @GetMapping("/retraits/hors-ligne")
    @PreAuthorize(Autorisations.PDV_MAGASIN)
    @Operation(summary = "Bons a garder sur l'appareil pour continuer a servir sans connexion")
    public List<RetraitService.Bon> pourHorsLigne(@AuthenticationPrincipal UtilisateurPrincipal principal) {
        return retraits.pourHorsLigne(principal);
    }

    @PostMapping("/retraits/synchronisation")
    @PreAuthorize(Autorisations.PDV_MAGASIN)
    @Operation(summary = "Envoie les remises faites sans connexion ; les impossibles deviennent des conflits")
    public List<RetraitService.ResultatSynchro> synchroniser(@Valid @RequestBody List<RetraitService.@Valid RemiseDifferee> remises,
                                                             @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return retraits.synchroniser(remises, principal);
    }

    @GetMapping("/retraits/conflits")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Remises sans connexion que le serveur n'a pas pu enregistrer")
    public List<RetraitService.Conflit> conflits() {
        return retraits.conflits();
    }

    @PostMapping("/retraits/conflits/{id}/traitement")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marque un conflit comme regle")
    public void traiterConflit(@PathVariable Long id, @AuthenticationPrincipal UtilisateurPrincipal principal) {
        retraits.traiterConflit(id, principal);
    }

    @PostMapping("/retraits/{id}/remise")
    @PreAuthorize(Autorisations.PDV_MAGASIN)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Sert tout ou partie du bon : le stock du lieu sort, un bon de livraison est emis")
    public MateriauxDto.BonReponse remettre(@PathVariable Long id, @Valid @RequestBody RetraitService.Remise r,
                                            @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return retraits.remettre(id, r, principal);
    }

    @PostMapping("/retraits/{id}/lieu")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Change le lieu de retrait, si le nouveau lieu a la marchandise")
    public RetraitService.Bon changerLieu(@PathVariable Long id, @Valid @RequestBody RetraitService.ChangementLieu r,
                                          @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return retraits.changerLieu(id, r.lieuId(), principal);
    }

    @GetMapping("/ventes/{id}/retraits")
    @PreAuthorize(Autorisations.PDV_CAISSE)
    @Operation(summary = "Bons de retrait d'une vente, a remettre au client")
    public List<RetraitService.Bon> retraitsDeLaVente(@PathVariable Long id) {
        return retraits.deLaVente(id);
    }

    @GetMapping("/retraits/{id}/pdf")
    @PreAuthorize(Autorisations.PDV_LIEUX)
    @Operation(summary = "Bon de retrait au format PDF, avec son code QR")
    public ResponseEntity<byte[]> retraitPdf(@PathVariable Long id) {
        return pdf(documents.bonRetrait(id), documents.nomBonRetrait(id));
    }

    @GetMapping("/retraits/livraisons/{id}/pdf")
    @PreAuthorize(Autorisations.PDV_MAGASIN)
    @Operation(summary = "Bon de livraison (sans prix) a faire signer au moment de la remise")
    public ResponseEntity<byte[]> livraisonPdf(@PathVariable Long id) {
        return pdf(documents.bonLivraison(id), documents.nomBonLivraison(id));
    }

    // --------------------------------------------------------------- transferts

    @GetMapping("/transferts")
    @PreAuthorize(Autorisations.PDV_MAGASIN)
    @Operation(summary = "Transferts qui concernent les lieux de l'utilisateur")
    public Page<TransfertService.Reponse> transferts(@RequestParam(defaultValue = "false") boolean enCours,
                                                     @PageableDefault(size = 30) Pageable pageable,
                                                     @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return transferts.lister(enCours, principal, pageable);
    }

    @GetMapping("/transferts/{id}")
    @PreAuthorize(Autorisations.PDV_MAGASIN)
    @Operation(summary = "Detail d'un transfert")
    public TransfertService.Reponse transfert(@PathVariable Long id, @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return transferts.detail(id, principal);
    }

    @PostMapping("/transferts")
    @PreAuthorize(Autorisations.PDV_MAGASIN)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Demande un transfert entre deux lieux")
    public TransfertService.Reponse demander(@Valid @RequestBody TransfertService.Demande r,
                                             @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return transferts.demander(r, principal);
    }

    @PostMapping("/transferts/{id}/validation")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Valide un transfert (gerant)")
    public TransfertService.Reponse valider(@PathVariable Long id, @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return transferts.valider(id, principal);
    }

    @PostMapping("/transferts/{id}/annulation")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Refuse ou annule un transfert qui n'est pas encore parti")
    public TransfertService.Reponse annuler(@PathVariable Long id, @Valid @RequestBody TransfertService.Annulation r,
                                            @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return transferts.annuler(id, r.motif(), principal);
    }

    @PostMapping("/transferts/{id}/expedition")
    @PreAuthorize(Autorisations.PDV_MAGASIN)
    @Operation(summary = "Expedie : la marchandise quitte le lieu de depart et passe en transit")
    public TransfertService.Reponse expedier(@PathVariable Long id, @Valid @RequestBody(required = false) TransfertService.Expedition r,
                                             @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return transferts.expedier(id, r, principal);
    }

    @PostMapping("/transferts/{id}/reception")
    @PreAuthorize(Autorisations.PDV_MAGASIN)
    @Operation(summary = "Receptionne : ce qui manque devient un ecart a traiter par le gerant")
    public TransfertService.Reponse receptionner(@PathVariable Long id, @Valid @RequestBody(required = false) TransfertService.Reception r,
                                                 @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return transferts.receptionner(id, r, principal);
    }

    @PostMapping("/transferts/{id}/ecart")
    @PreAuthorize(Autorisations.PDV_GESTION)
    @Operation(summary = "Decide du sort d'un ecart : perdu, revenu au depart ou finalement arrive")
    public TransfertService.Reponse ecart(@PathVariable Long id, @Valid @RequestBody TransfertService.DecisionEcart r,
                                          @AuthenticationPrincipal UtilisateurPrincipal principal) {
        return transferts.traiterEcart(id, r, principal);
    }

    @GetMapping("/transferts/{id}/pdf")
    @PreAuthorize(Autorisations.PDV_MAGASIN)
    @Operation(summary = "Bon de transfert au format PDF (A4)")
    public ResponseEntity<byte[]> transfertPdf(@PathVariable Long id) {
        return pdf(documents.bonTransfert(id), documents.nomBonTransfert(id));
    }

    private static ResponseEntity<byte[]> pdf(byte[] contenu, String nom) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(nom).build().toString())
                .body(contenu);
    }
}
