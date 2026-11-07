package tg.parcauto.api.pointdevente.depense;

/**
 * Moyen de payer une depense ou un fournisseur. Seul {@link #CAISSE} touche a
 * une session de caisse : il y cree une sortie d'especes.
 */
public enum ModeSortie {

    CAISSE("Especes de la caisse"),
    ESPECES("Especes hors caisse"),
    BANQUE("Banque (virement, cheque)"),
    MOBILE_MONEY("Mobile money");

    private final String libelle;

    ModeSortie(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
