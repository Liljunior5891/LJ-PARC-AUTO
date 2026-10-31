package tg.parcauto.api.pointdevente.vente;

/** Moyens d'encaissement acceptes en caisse. Seules les especes entrent dans le tiroir. */
public enum ModePaiementPdv {

    ESPECES("Especes"),
    MOBILE_MONEY("Mobile money"),
    CARTE("Carte bancaire"),
    AUTRE("Autre");

    private final String libelle;

    ModePaiementPdv(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
