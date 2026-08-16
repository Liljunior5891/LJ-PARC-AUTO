package tg.parcauto.api.maintenance.entretien;

/** Natures d'interventions, chapitre 19 du cahier des charges. */
public enum TypeEntretien {

    VIDANGE("Vidange", 10_000L, 180),
    FILTRES("Remplacement des filtres", 20_000L, 365),
    FREINS("Revision des freins", 40_000L, 365),
    COURROIE("Courroie de distribution", 120_000L, null),
    REVISION_GENERALE("Revision generale", 50_000L, 365),
    VISITE_TECHNIQUE("Visite technique", null, 365),
    REPARATION("Reparation", null, null),
    CARROSSERIE("Carrosserie", null, null),
    AUTRE("Autre intervention", null, null);

    private final String libelle;
    /** Periodicite conseillee en kilometres, nulle si l'intervention est ponctuelle. */
    private final Long periodiciteKmParDefaut;
    /** Periodicite conseillee en jours. */
    private final Integer periodiciteJoursParDefaut;

    TypeEntretien(String libelle, Long periodiciteKmParDefaut, Integer periodiciteJoursParDefaut) {
        this.libelle = libelle;
        this.periodiciteKmParDefaut = periodiciteKmParDefaut;
        this.periodiciteJoursParDefaut = periodiciteJoursParDefaut;
    }

    public String getLibelle() {
        return libelle;
    }

    public Long getPeriodiciteKmParDefaut() {
        return periodiciteKmParDefaut;
    }

    public Integer getPeriodiciteJoursParDefaut() {
        return periodiciteJoursParDefaut;
    }

    /** Vrai si l'intervention se repete a intervalle regulier. */
    public boolean periodique() {
        return periodiciteKmParDefaut != null || periodiciteJoursParDefaut != null;
    }
}
