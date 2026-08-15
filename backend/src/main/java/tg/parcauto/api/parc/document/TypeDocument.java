package tg.parcauto.api.parc.document;

/** Types de documents suivis, chapitres 21 et 8 du cahier des charges. */
public enum TypeDocument {

    CARTE_GRISE("Carte grise", true),
    ASSURANCE("Assurance", true),
    VISITE_TECHNIQUE("Visite technique", true),
    TAXE("Taxe", true),
    AUTORISATION("Autorisation de transport", true),
    DOCUMENT_DOUANIER("Document douanier", true),
    CONTRAT("Contrat", true),
    PERMIS_CONDUIRE("Permis de conduire", true),
    PIECE_IDENTITE("Piece d'identite", true),
    VISITE_MEDICALE("Visite medicale", true),
    AUTRE("Autre", false);

    private final String libelle;
    /** Vrai si ce type de document possede normalement une date d'expiration a surveiller. */
    private final boolean expirable;

    TypeDocument(String libelle, boolean expirable) {
        this.libelle = libelle;
        this.expirable = expirable;
    }

    public String getLibelle() {
        return libelle;
    }

    public boolean isExpirable() {
        return expirable;
    }
}
