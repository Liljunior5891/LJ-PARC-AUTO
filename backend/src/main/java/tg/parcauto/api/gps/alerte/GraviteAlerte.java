package tg.parcauto.api.gps.alerte;

public enum GraviteAlerte {

    INFORMATION("Information"),
    AVERTISSEMENT("Avertissement"),
    CRITIQUE("Critique");

    private final String libelle;

    GraviteAlerte(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
