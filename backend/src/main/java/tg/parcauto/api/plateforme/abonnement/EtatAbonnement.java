package tg.parcauto.api.plateforme.abonnement;

/** Etat calcule a partir des dates de l'abonnement et des reglages. */
public enum EtatAbonnement {
    ESSAI("Essai gratuit"),
    ACTIF("Actif"),
    /** Actif, mais l'echeance approche : le rappel s'affiche. */
    A_RENOUVELER("A renouveler"),
    /** Echeance depassee, jours de grace en cours : tout fonctionne encore. */
    GRACE("Periode de grace"),
    /** Grace epuisee : l'entreprise ne peut plus que consulter. */
    EXPIRE("Expire - lecture seule");

    private final String libelle;

    EtatAbonnement(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
