package tg.parcauto.api.pointdevente.stock;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.security.Autorisations;

@RestController
@ModuleRequis(ModuleApplicatif.POINT_DE_VENTE)
@RequestMapping("/api/pdv/stock")
@RequiredArgsConstructor
@PreAuthorize(Autorisations.PDV_GESTION)
@Tag(name = "Point de vente - stock")
public class StockController {

    private final StockService service;

    @GetMapping("/mouvements")
    @Operation(summary = "Historique des mouvements, tous articles ou un seul")
    public Page<StockService.Reponse> mouvements(@RequestParam(required = false) Long articleId,
                                                 @PageableDefault(size = 30) Pageable pageable) {
        return service.historique(articleId, pageable);
    }

    @PostMapping("/inventaire")
    @Operation(summary = "Valide une feuille d'inventaire : un mouvement pour chaque ecart constate")
    public StockService.BilanInventaire inventaire(@Valid @RequestBody StockService.Inventaire inventaire) {
        return service.inventaire(inventaire);
    }

    @PostMapping("/mouvements")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Saisit une reception, une sortie, une perte ou un inventaire")
    public StockService.Reponse saisir(@Valid @RequestBody StockService.Saisie saisie) {
        return service.saisir(saisie);
    }
}
