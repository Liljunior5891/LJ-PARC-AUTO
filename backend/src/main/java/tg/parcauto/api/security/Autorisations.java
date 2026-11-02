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

    /** Point de vente : catalogue, stock, annulations et rapports. */
    public static final String PDV_GESTION = "hasAnyRole('ADMIN','GERANT')";

    /** Point de vente : encaissement, especes et addition ; le serveur n'encaisse pas. */
    public static final String PDV_CAISSE = "hasAnyRole('ADMIN','GERANT','CAISSIER')";

    /** Point de vente : savoir si sa caisse est ouverte (le serveur ne prend de commande que caisse ouverte). */
    public static final String PDV_SESSIONS = "hasAnyRole('ADMIN','GERANT','CAISSIER','SERVEUR')";

    /** Bar / restaurant : plan de salle et commandes des tables. */
    public static final String PDV_SALLE = "hasAnyRole('ADMIN','GERANT','CAISSIER','SERVEUR')";

    /**
     * Bar / restaurant : envoi des commandes en preparation, reserve a la caisse.
     * Le serveur transmet ses commandes a la caisse, qui les valide et les envoie.
     */
    public static final String PDV_ENVOI = "hasAnyRole('ADMIN','GERANT','CAISSIER')";

    /** Bar / restaurant : ecrans de preparation de la cuisine et du bar. */
    public static final String PDV_PREPARATION = "hasAnyRole('ADMIN','GERANT','PREPARATEUR')";

    /** Point de vente : tout le personnel, pour les notifications de son poste. */
    public static final String PDV_PERSONNEL = "hasAnyRole('ADMIN','GERANT','CAISSIER','SERVEUR','PREPARATEUR')";

    /** Reserve a l'exploitant de la plateforme : entreprises abonnees. */
    public static final String PLATEFORME = "hasRole('SUPER_ADMIN')";

    private Autorisations() {
    }
}
