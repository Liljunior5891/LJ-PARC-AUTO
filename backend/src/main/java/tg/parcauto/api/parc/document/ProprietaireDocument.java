package tg.parcauto.api.parc.document;

/** Entite a laquelle un document est rattache. */
public enum ProprietaireDocument {
    CAMION,
    CHAUFFEUR,
    CLIENT,
    /** Client du point de vente : ses pieces ne s'affichent pas dans les ecrans du parc. */
    CLIENT_PDV
}
