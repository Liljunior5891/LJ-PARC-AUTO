package tg.parcauto.api.maintenance.incident;

public enum GraviteIncident {

    FAIBLE("Faible"),
    MOYENNE("Moyenne"),
    ELEVEE("Elevee"),
    CRITIQUE("Critique");

    private final String libelle;

    GraviteIncident(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
