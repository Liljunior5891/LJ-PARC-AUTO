package tg.parcauto.api.finance.depense;

/**
 * Statut d'une depense du parc. ATTENTE_ADMIN : saisie par le gestionnaire
 * alors que la validation est activee ; elle ne compte dans aucun resultat
 * avant l'approbation. REFUSEE et ANNULEE restent visibles mais ne comptent plus.
 */
public enum StatutDepense {

    ATTENTE_ADMIN("Attend l'administrateur"),
    VALIDEE("Validee"),
    REFUSEE("Refusee"),
    ANNULEE("Annulee");

    private final String libelle;

    StatutDepense(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    /** Depense qui compte, ou comptera une fois approuvee. */
    public boolean active() {
        return this == VALIDEE || this == ATTENTE_ADMIN;
    }
}
