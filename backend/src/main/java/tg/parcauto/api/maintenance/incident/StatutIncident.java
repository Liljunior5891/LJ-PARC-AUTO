package tg.parcauto.api.maintenance.incident;

public enum StatutIncident {

    DECLARE("Declare"),
    PRIS_EN_CHARGE("Pris en charge"),
    EN_REPARATION("En reparation"),
    RESOLU("Resolu"),
    CLOS("Clos");

    private final String libelle;

    StatutIncident(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    public boolean clos() {
        return this == RESOLU || this == CLOS;
    }
}
