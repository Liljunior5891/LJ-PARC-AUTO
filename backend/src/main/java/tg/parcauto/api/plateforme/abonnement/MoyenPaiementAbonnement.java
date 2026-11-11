package tg.parcauto.api.plateforme.abonnement;

public enum MoyenPaiementAbonnement {
    MOBILE_MONEY("Mobile Money"),
    /** Paye en ligne par PayGate Global (Flooz, T-Money), valide automatiquement apres verification. */
    PAYGATE("Mobile money en ligne"),
    VIREMENT("Virement bancaire"),
    ESPECES("Especes"),
    CHEQUE("Cheque"),
    AUTRE("Autre");

    private final String libelle;

    MoyenPaiementAbonnement(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
