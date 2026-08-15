package tg.parcauto.api.parc.camion;

/** Statuts du camion, chapitre 6.2 du cahier des charges. */
public enum StatutCamion {

    DISPONIBLE("Disponible"),
    EN_LOCATION("En location"),
    EN_MISSION("En mission"),
    EN_LIVRAISON("En livraison"),
    EN_MAINTENANCE("En maintenance"),
    IMMOBILISE("Immobilise"),
    ACCIDENTE("Accidente"),
    HORS_SERVICE("Hors service"),
    VENDU("Vendu");

    private final String libelle;

    StatutCamion(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    /** Un camion vendu ou hors service ne peut plus etre exploite. */
    public boolean exploitable() {
        return this != VENDU && this != HORS_SERVICE;
    }

    /** Le camion peut recevoir une nouvelle affectation ou mission. */
    public boolean affectable() {
        return this == DISPONIBLE;
    }
}
