package tg.parcauto.api.parc.chauffeur;

/** Lien du chauffeur avec l'entreprise : il decide des informations demandees. */
public enum TypeChauffeur {

    SALARIE("Salarie"),
    JOURNALIER("Journalier"),
    PRESTATAIRE("Prestataire independant");

    private final String libelle;

    TypeChauffeur(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
