package tg.parcauto.api.finance.facture;

/** Statuts de facture et de reglement, chapitres 27 et 28. */
public enum StatutFacture {

    BROUILLON("Brouillon"),
    NON_PAYEE("Non payee"),
    PARTIELLEMENT_PAYEE("Partiellement payee"),
    PAYEE("Payee"),
    EN_RETARD("En retard"),
    ANNULEE("Annulee");

    private final String libelle;

    StatutFacture(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    /** La facture est emise et attend un reglement. */
    public boolean attendReglement() {
        return this == NON_PAYEE || this == PARTIELLEMENT_PAYEE || this == EN_RETARD;
    }

    public boolean modifiable() {
        return this == BROUILLON;
    }

    public boolean clos() {
        return this == PAYEE || this == ANNULEE;
    }
}
