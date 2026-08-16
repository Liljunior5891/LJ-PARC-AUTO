package tg.parcauto.api.maintenance.entretien;

public enum StatutEntretien {

    PLANIFIE("Planifie"),
    EN_COURS("En cours"),
    REALISE("Realise"),
    ANNULE("Annule");

    private final String libelle;

    StatutEntretien(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    /** L'intervention mobilise le camion a l'atelier. */
    public boolean immobilise() {
        return this == EN_COURS;
    }

    public boolean clos() {
        return this == REALISE || this == ANNULE;
    }
}
