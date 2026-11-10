package tg.parcauto.api.pointdevente.vente;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Prix unitaires d'une commande passee en ligne, par article : a l'encaissement,
 * le ticket reprend le prix paye par le client (prix de l'offre), pas celui du
 * catalogue. Fourni par la marketplace.
 */
public interface PrixCommandesEnLigne {

    Map<Long, BigDecimal> prixParArticle(Long commandeEnLigneId);
}
