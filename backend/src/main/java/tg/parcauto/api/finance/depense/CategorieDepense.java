package tg.parcauto.api.finance.depense;

/** Categories de depenses, chapitre 16 du cahier des charges. */
public enum CategorieDepense {

    CARBURANT("Carburant"),
    ENTRETIEN("Entretien"),
    PNEUS("Pneus"),
    EXPLOITATION("Exploitation"),
    ADMINISTRATIF("Administratif"),
    PERSONNEL("Personnel"),
    AUTRE("Autres depenses"),
    /** Charge de l'entreprise sans vehicule (loyer, salaires du bureau...) : le detail est dans sa categorie modifiable. */
    CHARGE_GENERALE("Charges generales");

    private final String libelle;

    CategorieDepense(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
