package tg.parcauto.api.pointdevente.catalogue;

/**
 * Poste ou se prepare un article commande a table. Les articles servis tels
 * quels (bouteille, dessert du jour) n'en ont pas : ils sont prets des la
 * commande envoyee.
 */
public enum PostePreparation {

    AUCUN("Servi directement"),
    CUISINE("Cuisine"),
    BAR("Bar");

    private final String libelle;

    PostePreparation(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
