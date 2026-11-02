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
    GARANT_PIECE_IDENTITE("Piece d'identite du garant", false),
    GARANT_PERMIS_CONDUIRE("Permis de conduire du garant", false),
    GARANT_ATTESTATION("Attestation de garantie", false),
    GARANT_JUSTIFICATIF("Justificatif du garant", false),
    // --- Pieces des clients : particuliers, entreprises, administrations, ONG ---
    CARTE_IDENTITE("Carte nationale d'identite", true),
    PASSEPORT("Passeport", true),
    CARTE_ELECTEUR("Carte d'electeur", false),
    CARTE_SEJOUR("Carte de sejour", true),
    CARTE_CONSULAIRE("Carte consulaire", true),
    CARTE_OPERATEUR_ECONOMIQUE("Carte d'operateur economique", true),
    RCCM("Extrait RCCM", false),
    ATTESTATION_NIF("Attestation NIF", false),
    STATUTS("Statuts", false),
    PIECE_REPRESENTANT("Piece d'identite du representant", true),
    LETTRE_MANDAT("Bon de commande / lettre de mission", false),
    AGREMENT("Agrement", true),
    ACCORD_SIEGE("Accord de siege", false),
    // --- Pieces propres aux chauffeurs ---
    CASIER_JUDICIAIRE("Casier judiciaire", false),
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
