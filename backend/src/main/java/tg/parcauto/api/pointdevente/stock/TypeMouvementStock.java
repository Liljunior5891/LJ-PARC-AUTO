package tg.parcauto.api.pointdevente.stock;

/** Nature d'un mouvement de stock et son effet sur la quantite disponible. */
public enum TypeMouvementStock {

    ENTREE("Entree (reception)", 1, true),
    SORTIE("Sortie", -1, true),
    PERTE("Perte / casse", -1, true),
    /** Comptage physique : la quantite saisie remplace le stock, l'ecart est enregistre. */
    INVENTAIRE("Inventaire", 0, true),
    VENTE("Vente", -1, false),
    RETOUR("Retour (annulation de vente)", 1, false);

    private final String libelle;
    private final int sens;
    /** Faux pour les mouvements generes par la caisse, qu'on ne saisit pas a la main. */
    private final boolean manuel;

    TypeMouvementStock(String libelle, int sens, boolean manuel) {
        this.libelle = libelle;
        this.sens = sens;
        this.manuel = manuel;
    }

    public String getLibelle() {
        return libelle;
    }

    public int getSens() {
        return sens;
    }

    public boolean isManuel() {
        return manuel;
    }
}
