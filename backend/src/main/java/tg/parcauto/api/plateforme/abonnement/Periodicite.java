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

    /** Periodicite dont la remise s'applique a une duree libre : la plus longue atteinte. */
    public static Periodicite pour(int mois) {
        return mois >= 12 ? ANNUEL : mois >= 6 ? SEMESTRIEL : mois >= 3 ? TRIMESTRIEL : MENSUEL;
    }
}
