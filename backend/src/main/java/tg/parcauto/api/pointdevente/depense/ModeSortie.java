package tg.parcauto.api.pointdevente.depense;

/**
 * Moyen de payer une depense ou un fournisseur : la caisse (sortie d'especes de
 * la session ouverte), le coffre du gerant (circuit des especes), la banque,
 * c'est-a-dire le compte de l'entreprise, ou le fonds de reserve (depenses seulement). Especes hors caisse et mobile money ne
 * sont plus proposes : ils restent lisibles sur les pieces deja enregistrees.
 */
public enum ModeSortie {

    CAISSE("Caisse"),
    COFFRE("Coffre du gerant"),
    BANQUE("Banque (compte de l'entreprise)"),
    /** Fonds de reserve du point de vente (option des reglages), dans la limite de son solde. */
    RESERVE("Fonds de reserve"),
    ESPECES("Especes hors caisse"),
    MOBILE_MONEY("Mobile money");

    /** Moyens proposes a la saisie. */
    public boolean propose() {
        return this == CAISSE || this == COFFRE || this == BANQUE || this == RESERVE;
    }

    private final String libelle;

    ModeSortie(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
