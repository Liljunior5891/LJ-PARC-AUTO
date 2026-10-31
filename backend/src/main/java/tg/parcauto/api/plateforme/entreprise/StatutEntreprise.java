package tg.parcauto.api.plateforme.entreprise;

public enum StatutEntreprise {

    /** Les comptes de l'entreprise peuvent se connecter et travailler. */
    ACTIVE("Active"),
    /** Suspendue par la plateforme : plus aucune connexion, donnees conservees. */
    SUSPENDUE("Suspendue");

    private final String libelle;

    StatutEntreprise(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
