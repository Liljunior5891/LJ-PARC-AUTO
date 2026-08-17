package tg.parcauto.api.security;

/**
 * Expressions d'autorisation partagees par les controleurs.
 * <p>
 * Les regroupper ici evite de repeter la meme liste de roles dans chaque module
 * et garantit qu'un changement de profil se fait en un seul endroit.
 */
public final class Autorisations {

    /** Conduite de l'activite : parc, exploitation, finances, GPS, maintenance. */
    public static final String GESTION = "hasAnyRole('ADMIN','GESTIONNAIRE_PARC')";

    /** Actions de terrain, ouvertes en plus au chauffeur concerne. */
    public static final String TERRAIN = "hasAnyRole('ADMIN','GESTIONNAIRE_PARC','CHAUFFEUR')";

    /** Reserve a l'administrateur : comptes utilisateurs et parametrage sensible. */
    public static final String ADMINISTRATION = "hasRole('ADMIN')";

    private Autorisations() {
    }
}
