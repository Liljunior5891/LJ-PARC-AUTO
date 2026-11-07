package tg.parcauto.api.security;

/**
 * Profils utilisateurs de l'application.
 * <p>
 * Deux profils portent l'exploitation : l'administrateur, qui gere en plus les
 * comptes, et le gestionnaire de parc, qui conduit l'activite au quotidien. Le
 * profil chauffeur reste defini pour les actions de terrain.
 */
public enum Role {

    /** Exploitant de la plateforme : gere les entreprises abonnees, sans acces a leurs donnees. */
    SUPER_ADMIN("Administrateur plateforme"),
    /** Acces complet a son entreprise : comptes, modules et parametrage. */
    ADMIN("Administrateur"),
    /** Camions, chauffeurs, exploitation, finances, GPS et maintenance. */
    GESTIONNAIRE_PARC("Gestionnaire de parc"),
    /** Compte de terrain : missions personnelles, livraisons, incidents. */
    CHAUFFEUR("Chauffeur"),
    /** Point de vente : articles, stock, caisse, annulations et rapports. */
    GERANT("Gerant de boutique"),
    /** Point de vente : encaissement des ventes sur sa propre session de caisse. */
    CAISSIER("Caissier"),
    /** Bar / restaurant : tables, prise de commande et encaissement des additions. */
    SERVEUR("Serveur"),
    /** Bar / restaurant : ecrans de la cuisine et du bar. */
    PREPARATEUR("Cuisinier / Barman"),
    /** Quincaillerie avec entrepots : sert les bons de retrait et les transferts de ses lieux, sans voir les prix. */
    MAGASINIER("Magasinier"),
    /**
     * Point de vente (option « benefices des associes ») : ne voit que sa propre part
     * de la marge des produits auxquels il est rattache. Pour d'autres ecrans,
     * l'administrateur lui ajoute d'autres profils.
     */
    ASSOCIE("Associe");

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
