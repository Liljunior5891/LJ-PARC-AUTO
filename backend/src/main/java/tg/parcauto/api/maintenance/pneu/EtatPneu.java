package tg.parcauto.api.maintenance.pneu;

public enum EtatPneu {

    NEUF("Neuf"),
    BON("Bon etat"),
    USE("Use"),
    A_REMPLACER("A remplacer"),
    HORS_USAGE("Hors usage");

    private final String libelle;

    EtatPneu(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    /** Etats qui imposent un remplacement rapide. */
    public boolean critique() {
        return this == A_REMPLACER || this == HORS_USAGE;
    }
}
