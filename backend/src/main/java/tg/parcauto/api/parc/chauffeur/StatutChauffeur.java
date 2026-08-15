package tg.parcauto.api.parc.chauffeur;

public enum StatutChauffeur {

    ACTIF("Actif"),
    EN_MISSION("En mission"),
    EN_CONGE("En conge"),
    SUSPENDU("Suspendu"),
    INACTIF("Inactif");

    private final String libelle;

    StatutChauffeur(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    public boolean affectable() {
        return this == ACTIF;
    }
}
