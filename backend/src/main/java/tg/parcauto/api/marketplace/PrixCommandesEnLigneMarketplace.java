package tg.parcauto.api.marketplace;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.pointdevente.vente.PrixCommandesEnLigne;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** Prix des articles tels que commandes en ligne, pour le ticket de la caisse boutique. */
@Component
@RequiredArgsConstructor
public class PrixCommandesEnLigneMarketplace implements PrixCommandesEnLigne {

    private final CommandeEnLigneRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> prixParArticle(Long commandeEnLigneId) {
        Map<Long, BigDecimal> prix = new HashMap<>();
        repository.findById(commandeEnLigneId).ifPresent(c -> c.getLignes().stream()
                .filter(l -> l.getArticleId() != null)
                .forEach(l -> prix.putIfAbsent(l.getArticleId(), l.getPrixUnitaire())));
        return prix;
    }
}
