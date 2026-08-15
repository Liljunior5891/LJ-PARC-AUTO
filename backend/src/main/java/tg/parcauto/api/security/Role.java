package tg.parcauto.api.security;

/** Profils utilisateurs definis au chapitre 40 du cahier des charges. */
public enum Role {

    /** Acces complet. */
    ADMIN,
    /** Tableaux de bord, finances, rentabilite, GPS, rapports. */
    DIRECTION,
    /** Camions, chauffeurs, missions, maintenance, GPS. */
    GESTIONNAIRE_PARC,
    /** Recettes, depenses, factures, paiements. */
    COMPTABLE,
    /** Missions, locations, chauffeurs, camions. */
    LOGISTIQUE,
    /** Application mobile et missions personnelles. */
    CHAUFFEUR;

    public String authority() {
        return "ROLE_" + name();
    }
}
