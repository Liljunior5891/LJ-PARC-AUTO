package tg.parcauto.api.plateforme.abonnement;

/**
 * Volumes limites par la formule d'abonnement, et la table ou les compter.
 * Seuls les elements actifs comptent : archiver ou desactiver libere une place,
 * et la reactivation repasse par la limite.
 */
public enum Ressource {
    UTILISATEURS("Comptes utilisateurs", "utilisateur", " AND actif"),
    VEHICULES("Vehicules", "camion", " AND actif"),
    AGENCES("Agences", "agc_agence", " AND actif"),
    CAISSES("Caisses de point de vente", "pdv_caisse", " AND actif"),
    EMPLOYES("Employes (presence)", "pres_employe", " AND actif");

    private final String libelle;
    private final String table;
    private final String condition;

    Ressource(String libelle, String table, String condition) {
        this.libelle = libelle;
        this.table = table;
        this.condition = condition;
    }

    public String getLibelle() {
        return libelle;
    }

    String requeteComptage() {
        return "SELECT count(*) FROM " + table + " WHERE entreprise_id = ?" + condition;
    }

    String requeteParEntreprise() {
        return "SELECT entreprise_id, count(*) FROM " + table + " WHERE entreprise_id IS NOT NULL" + condition
                + " GROUP BY entreprise_id";
    }
}
