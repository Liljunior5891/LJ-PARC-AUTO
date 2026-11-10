package tg.parcauto.api.plateforme.abonnement;

/** Duree payee d'avance ; les periodes longues beneficient d'une remise reglable. */
public enum Periodicite {
    MENSUEL("Mensuel", 1),
    TRIMESTRIEL("Trimestriel", 3),
    SEMESTRIEL("Semestriel", 6),
    ANNUEL("Annuel", 12);

    private final String libelle;
    private final int mois;

    Periodicite(String libelle, int mois) {
        this.libelle = libelle;
        this.mois = mois;
    }

    public String getLibelle() {
        return libelle;
    }

    public int getMois() {
        return mois;
    }
}
