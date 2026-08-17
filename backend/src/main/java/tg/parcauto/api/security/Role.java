package tg.parcauto.api.security;

/**
 * Profils utilisateurs de l'application.
 * <p>
 * Deux profils suffisent a l'exploitation : l'administrateur, qui gere en plus
 * les comptes, et le gestionnaire de parc, qui conduit l'activite au quotidien.
 */
public enum Role {

    /** Acces complet, y compris la gestion des utilisateurs. */
    ADMIN("Administrateur"),
    /** Camions, chauffeurs, exploitation, finances, GPS et maintenance. */
    GESTIONNAIRE_PARC("Gestionnaire de parc");

    private final String libelle;

    Role(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    public String authority() {
        return "ROLE_" + name();
    }
}
