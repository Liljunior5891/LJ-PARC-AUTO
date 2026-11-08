package tg.parcauto.api.agence.tresorerie;

/** Origine d'une ecriture du registre. */
public enum NatureEcriture {

    OUVERTURE("Solde d'ouverture"),
    TRANSACTION("Transaction"),
    MOUVEMENT("Mouvement entre comptes"),
    REGULARISATION("Regularisation"),
    ANNULATION("Annulation"),
    DEPENSE("Depense"),
    CREDIT("Credit ou dette"),
    CARTE("Cartes prepayees");

    private final String libelle;

    NatureEcriture(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
