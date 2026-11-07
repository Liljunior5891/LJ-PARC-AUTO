package tg.parcauto.api.parc.document;

/** Entite a laquelle un document est rattache. */
public enum ProprietaireDocument {
    CAMION,
    CHAUFFEUR,
    CLIENT,
    /** Client du point de vente : ses pieces ne s'affichent pas dans les ecrans du parc. */
    CLIENT_PDV,
    /** Fournisseur du point de vente : comme les clients du PDV, hors des ecrans du parc. */
    FOURNISSEUR_PDV;

    /** Pieces du point de vente, absentes des listes et alertes du parc. */
    public boolean pointDeVente() {
        return this == CLIENT_PDV || this == FOURNISSEUR_PDV;
    }
}
