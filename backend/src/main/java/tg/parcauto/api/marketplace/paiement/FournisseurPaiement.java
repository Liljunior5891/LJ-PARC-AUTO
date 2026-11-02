package tg.parcauto.api.marketplace.paiement;

import tg.parcauto.api.marketplace.CommandeEnLigne;

/**
 * Fournisseur de paiement mobile money (agregateur type CinetPay, PayDunya,
 * FedaPay...). Brancher un vrai fournisseur : une implementation de cette
 * interface, activee par {@code parcauto.paiement.fournisseur}, qui cree la
 * transaction et renvoie l'adresse ou le client valide le paiement ; la
 * confirmation arrive ensuite par son webhook, qui appelle
 * {@link PaiementService#confirmer(CommandeEnLigne, String)}.
 */
public interface FournisseurPaiement {

    /** Code du fournisseur, tel qu'ecrit dans la configuration. */
    String code();

    /** Cree la transaction et renvoie sa reference et l'adresse de paiement. */
    Initiation initier(CommandeEnLigne commande);

    /** Vrai pour le fournisseur de demonstration, dont le client confirme lui-meme le paiement. */
    default boolean simule() {
        return false;
    }

    record Initiation(String reference, String urlPaiement) {
    }
}
