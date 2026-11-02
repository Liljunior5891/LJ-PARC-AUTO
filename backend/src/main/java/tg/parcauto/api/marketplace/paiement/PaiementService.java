package tg.parcauto.api.marketplace.paiement;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.marketplace.CommandeEnLigne;

import java.util.Optional;

/**
 * Point d'entree du paiement en ligne. Sans fournisseur configure, le mobile
 * money n'est simplement pas propose aux clients : seules restent les
 * commandes payees au retrait ou a la livraison.
 */
@Service
public class PaiementService {

    private final Optional<FournisseurPaiement> fournisseur;

    public PaiementService(ObjectProvider<FournisseurPaiement> fournisseurs) {
        this.fournisseur = Optional.ofNullable(fournisseurs.getIfAvailable());
    }

    public boolean disponible() {
        return fournisseur.isPresent();
    }

    public boolean simule() {
        return fournisseur.map(FournisseurPaiement::simule).orElse(false);
    }

    /** Lance le paiement d'une commande et note sa reference ; renvoie l'adresse de paiement. */
    public String initier(CommandeEnLigne commande) {
        FournisseurPaiement f = fournisseur.orElseThrow(() ->
                new BusinessException("Le paiement en ligne n'est pas disponible pour le moment"));
        FournisseurPaiement.Initiation initiation = f.initier(commande);
        commande.setReferencePaiement(initiation.reference());
        return initiation.urlPaiement();
    }

    /**
     * Paiement confirme par le fournisseur : la reference doit etre celle de la
     * transaction lancee pour cette commande.
     */
    public void confirmer(CommandeEnLigne commande, String reference) {
        if (commande.getReferencePaiement() == null || !commande.getReferencePaiement().equals(reference)) {
            throw new BusinessException("Reference de paiement inconnue pour cette commande");
        }
        commande.setStatutPaiement(CommandeEnLigne.StatutPaiement.PAYEE);
    }
}
