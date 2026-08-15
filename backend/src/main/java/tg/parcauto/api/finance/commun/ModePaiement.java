package tg.parcauto.api.finance.commun;

public enum ModePaiement {

    ESPECES("Especes"),
    VIREMENT("Virement bancaire"),
    CHEQUE("Cheque"),
    MOBILE_MONEY("Mobile money"),
    CARTE("Carte bancaire"),
    COMPENSATION("Compensation"),
    AUTRE("Autre");

    private final String libelle;

    ModePaiement(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
