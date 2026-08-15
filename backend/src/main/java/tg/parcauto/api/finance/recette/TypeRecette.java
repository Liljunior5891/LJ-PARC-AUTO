package tg.parcauto.api.finance.recette;

/** Types de recettes, chapitre 15 du cahier des charges. */
public enum TypeRecette {

    LOCATION("Location"),
    TRANSPORT("Transport"),
    LIVRAISON("Livraison"),
    MISSION_SPECIALE("Mission speciale"),
    VOYAGE("Voyage"),
    CONTRAT("Contrat"),
    FRAIS_SUPPLEMENTAIRE("Frais supplementaires"),
    KILOMETRAGE_SUPPLEMENTAIRE("Kilometrage supplementaire"),
    AUTRE("Autre recette");

    private final String libelle;

    TypeRecette(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }
}
