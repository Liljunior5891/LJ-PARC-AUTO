package tg.parcauto.api.finance.depense;

/** Categories de depenses, chapitre 16 du cahier des charges. */
public enum CategorieDepense {

    CARBURANT("Carburant"),
    ENTRETIEN("Entretien"),
    PNEUS("Pneus"),
    EXPLOITATION("Exploitation"),
    ADMINISTRATIF("Administratif"),
    PERSONNEL("Personnel"),
    AUTRE("Autres depenses");

    private final String libelle;

    CategorieDepense(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
