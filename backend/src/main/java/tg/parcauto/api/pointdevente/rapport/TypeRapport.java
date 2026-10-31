package tg.parcauto.api.pointdevente.rapport;

/** Rapports du point de vente ; ceux de la restauration ne s'ouvrent qu'aux restaurants. */
public enum TypeRapport {

    VENTES("Commandes encaissees", "Ventes", true),
    VENTES_PRODUITS("Ventes par produit", "Ventes", true),
    VENTES_CATEGORIES("Ventes par categorie", "Ventes", true),
    VENTES_VENDEURS("Ventes par vendeur", "Ventes", true),
    VENTES_JOURS("Ventes par jour", "Ventes", true),
    PRODUITS("Liste des produits", "Stock", false),
    STOCK("Etat du stock", "Stock", false),
    FICHE_STOCK("Fiche de stock", "Stock", true, true),
    MOUVEMENTS("Mouvements de stock", "Stock", true),
    COMMANDES_FOURNISSEURS("Commandes fournisseurs", "Achats", true),
    CAISSES("Sessions de caisse", "Caisses", true),
    COMMANDES_TABLE("Commandes de table", "Restaurant", true),
    PREPARATION_BONS("Bons par ecran de preparation", "Restaurant", true, false, true),
    PREPARATION_PRODUITS("Produits par ecran de preparation", "Restaurant", true, false, true);

    private final String libelle;
    private final String famille;
    /** Faux pour un etat a l'instant (le stock), sans periode. */
    private final boolean periode;
    /** Vrai pour un rapport qui porte sur un seul produit (la fiche de stock). */
    private final boolean parArticle;
    /** Vrai pour un rapport qui porte sur un ecran de preparation (cuisine, bar...). */
    private final boolean parEcran;

    TypeRapport(String libelle, String famille, boolean periode) {
        this(libelle, famille, periode, false, false);
    }

    TypeRapport(String libelle, String famille, boolean periode, boolean parArticle) {
        this(libelle, famille, periode, parArticle, false);
    }

    TypeRapport(String libelle, String famille, boolean periode, boolean parArticle, boolean parEcran) {
        this.libelle = libelle;
        this.famille = famille;
        this.periode = periode;
        this.parArticle = parArticle;
        this.parEcran = parEcran;
    }

    public boolean isParEcran() {
        return parEcran;
    }

    /** Rapports propres aux bars et restaurants. */
    public boolean isRestauration() {
        return this == COMMANDES_TABLE || parEcran;
    }

    public boolean isParArticle() {
        return parArticle;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getFamille() {
        return famille;
    }

    public boolean isPeriode() {
        return periode;
    }
}
