package tg.parcauto.api.finance.depense;

import java.util.Arrays;
import java.util.List;

/**
 * Nature precise de la depense, telle que detaillee au chapitre 16.
 * Chaque nature porte sa categorie : la categorie de la depense en est deduite,
 * ce qui evite les incoherences de saisie.
 */
public enum SousCategorieDepense {

    // Carburant
    DIESEL("Diesel", CategorieDepense.CARBURANT),
    ESSENCE("Essence", CategorieDepense.CARBURANT),
    AUTRE_CARBURANT("Autre carburant", CategorieDepense.CARBURANT),

    // Entretien
    VIDANGE("Vidange", CategorieDepense.ENTRETIEN),
    FILTRES("Filtres", CategorieDepense.ENTRETIEN),
    FREINS("Freins", CategorieDepense.ENTRETIEN),
    MOTEUR("Moteur", CategorieDepense.ENTRETIEN),
    BATTERIE("Batterie", CategorieDepense.ENTRETIEN),
    ELECTRICITE("Electricite", CategorieDepense.ENTRETIEN),
    CLIMATISATION("Climatisation", CategorieDepense.ENTRETIEN),
    SUSPENSION("Suspension", CategorieDepense.ENTRETIEN),
    REPARATION("Reparation", CategorieDepense.ENTRETIEN),

    // Pneus
    ACHAT_PNEU("Achat de pneus", CategorieDepense.PNEUS),
    MONTAGE_PNEU("Montage", CategorieDepense.PNEUS),
    REPARATION_PNEU("Reparation de pneu", CategorieDepense.PNEUS),
    EQUILIBRAGE("Equilibrage", CategorieDepense.PNEUS),
    PARALLELISME("Parallelisme", CategorieDepense.PNEUS),

    // Exploitation
    PEAGE("Peage", CategorieDepense.EXPLOITATION),
    PARKING("Parking", CategorieDepense.EXPLOITATION),
    LAVAGE("Lavage", CategorieDepense.EXPLOITATION),
    DEPANNAGE("Depannage", CategorieDepense.EXPLOITATION),
    FRAIS_ROUTE("Frais de route", CategorieDepense.EXPLOITATION),
    FRAIS_MISSION("Frais de mission", CategorieDepense.EXPLOITATION),

    // Administratif
    ASSURANCE("Assurance", CategorieDepense.ADMINISTRATIF),
    TAXE("Taxes", CategorieDepense.ADMINISTRATIF),
    VISITE_TECHNIQUE("Visite technique", CategorieDepense.ADMINISTRATIF),
    AUTORISATION("Autorisation", CategorieDepense.ADMINISTRATIF),
    GPS("Abonnement GPS", CategorieDepense.ADMINISTRATIF),

    // Personnel
    SALAIRE_CHAUFFEUR("Salaire du chauffeur", CategorieDepense.PERSONNEL),
    PRIME_CHAUFFEUR("Prime du chauffeur", CategorieDepense.PERSONNEL),

    AUTRE("Autre depense", CategorieDepense.AUTRE);

    private final String libelle;
    private final CategorieDepense categorie;

    SousCategorieDepense(String libelle, CategorieDepense categorie) {
        this.libelle = libelle;
        this.categorie = categorie;
    }

    public String getLibelle() {
        return libelle;
    }

    public CategorieDepense getCategorie() {
        return categorie;
    }

    /** Vrai si cette nature correspond a un plein de carburant (chapitre 18). */
    public boolean estCarburant() {
        return categorie == CategorieDepense.CARBURANT;
    }

    public static List<SousCategorieDepense> de(CategorieDepense categorie) {
        return Arrays.stream(values()).filter(s -> s.categorie == categorie).toList();
    }
}
