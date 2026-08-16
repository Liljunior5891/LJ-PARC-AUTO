package tg.parcauto.api.maintenance.incident;

/** Incidents declarables par le chauffeur, chapitre 33. */
public enum TypeIncident {

    PANNE("Panne", true),
    ACCIDENT("Accident", true),
    CREVAISON("Crevaison", true),
    PROBLEME_MECANIQUE("Probleme mecanique", true),
    RETARD("Retard", false),
    VOL("Vol ou tentative de vol", true),
    CONTROLE_ROUTIER("Controle routier", false),
    AUTRE("Autre incident", false);

    private final String libelle;
    /** Vrai si l'incident immobilise generalement le vehicule. */
    private final boolean immobilisantParDefaut;

    TypeIncident(String libelle, boolean immobilisantParDefaut) {
        this.libelle = libelle;
        this.immobilisantParDefaut = immobilisantParDefaut;
    }

    public String getLibelle() {
        return libelle;
    }

    public boolean isImmobilisantParDefaut() {
        return immobilisantParDefaut;
    }
}
