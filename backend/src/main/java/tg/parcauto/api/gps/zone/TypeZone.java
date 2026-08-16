package tg.parcauto.api.gps.zone;

/** Natures de zones definies au chapitre 25. */
public enum TypeZone {

    SIEGE("Siege", "#0f4c81"),
    DEPOT("Depot", "#1e874b"),
    AGENCE("Agence", "#1565c0"),
    ZONE_EXPLOITATION("Zone d'exploitation", "#f0a202"),
    ZONE_INTERDITE("Zone interdite", "#c62828"),
    CLIENT("Site client", "#6b3fa0");

    private final String libelle;
    private final String couleurParDefaut;

    TypeZone(String libelle, String couleurParDefaut) {
        this.libelle = libelle;
        this.couleurParDefaut = couleurParDefaut;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getCouleurParDefaut() {
        return couleurParDefaut;
    }

    /** Toute entree dans une zone interdite doit declencher une alerte. */
    public boolean surveillanceObligatoire() {
        return this == ZONE_INTERDITE;
    }
}
