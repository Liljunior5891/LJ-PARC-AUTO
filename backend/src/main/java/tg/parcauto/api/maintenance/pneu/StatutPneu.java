package tg.parcauto.api.maintenance.pneu;

public enum StatutPneu {

    EN_STOCK("En stock"),
    MONTE("Monte"),
    DEPOSE("Depose"),
    REBUTE("Rebute");

    private final String libelle;

    StatutPneu(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
