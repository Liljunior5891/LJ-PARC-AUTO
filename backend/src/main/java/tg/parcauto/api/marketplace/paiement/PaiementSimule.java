package tg.parcauto.api.marketplace.paiement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tg.parcauto.api.marketplace.CommandeEnLigne;

/**
 * Paiement de demonstration, pour developper et tester sans agregateur : la
 * page de suivi affiche un bouton qui confirme le paiement. Ne s'active que
 * si {@code parcauto.paiement.fournisseur=simulation} ; a ne jamais activer
 * en production, puisque n'importe quel client pourrait se declarer paye.
 */
@Component
@ConditionalOnProperty(name = "parcauto.paiement.fournisseur", havingValue = "simulation")
public class PaiementSimule implements FournisseurPaiement {

    @Override
    public String code() {
        return "simulation";
    }

    @Override
    public Initiation initier(CommandeEnLigne commande) {
        return new Initiation("SIM-" + commande.getNumero(), "/suivi/" + commande.getJeton() + "?paiement=simulation");
    }

    @Override
    public boolean simule() {
        return true;
    }
}
