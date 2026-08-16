package tg.parcauto.api.gps.alerte;

/** Alertes generees par le module GPS, chapitre 26 du cahier des charges. */
public enum TypeAlerteGps {

    EXCES_VITESSE("Exces de vitesse", GraviteAlerte.CRITIQUE),
    ENTREE_ZONE("Entree dans une zone", GraviteAlerte.INFORMATION),
    SORTIE_ZONE("Sortie de zone", GraviteAlerte.AVERTISSEMENT),
    ZONE_INTERDITE("Entree en zone interdite", GraviteAlerte.CRITIQUE),
    STATIONNEMENT_PROLONGE("Arret prolonge", GraviteAlerte.AVERTISSEMENT),
    MOUVEMENT_HORS_MISSION("Mouvement hors mission", GraviteAlerte.AVERTISSEMENT),
    GPS_DECONNECTE("GPS deconnecte", GraviteAlerte.AVERTISSEMENT),
    HORS_HORAIRES("Utilisation hors horaires", GraviteAlerte.AVERTISSEMENT);

    private final String libelle;
    private final GraviteAlerte graviteParDefaut;

    TypeAlerteGps(String libelle, GraviteAlerte graviteParDefaut) {
        this.libelle = libelle;
        this.graviteParDefaut = graviteParDefaut;
    }

    public String getLibelle() {
        return libelle;
    }

    public GraviteAlerte getGraviteParDefaut() {
        return graviteParDefaut;
    }
}
