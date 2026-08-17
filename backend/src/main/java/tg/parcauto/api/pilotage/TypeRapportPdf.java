package tg.parcauto.api.pilotage;

import java.time.LocalDate;
import java.util.Locale;

/** Catalogue des rapports editables en PDF. */
public enum TypeRapportPdf {

    PARC_GLOBAL("Rapport global du parc",
            "Recettes, depenses et resultat de l'ensemble du parc, avec la ventilation par camion",
            false),
    CAMIONS_DETAIL("Rapport complet par camion",
            "Fiche detaillee d'un ou plusieurs camions : exploitation, finances, carburant, maintenance",
            true),
    FINANCIER("Rapport financier",
            "Chiffre d'affaires, depenses, resultat et creances",
            false),
    RECETTES("Journal des recettes",
            "Detail des recettes de la periode, par camion et par type",
            true),
    DEPENSES("Journal des depenses",
            "Detail des depenses de la periode, par categorie et par camion",
            true),
    CARBURANT("Rapport carburant",
            "Pleins, consommation moyenne et cout au kilometre",
            true),
    MAINTENANCE("Rapport de maintenance",
            "Interventions, echeances, pneumatiques et incidents",
            true),
    CHAUFFEURS("Rapport chauffeurs",
            "Effectif, permis, affectations et incidents declares",
            false),
    CREANCES("Etat des creances",
            "Factures emises non soldees et retards de reglement",
            false);

    private final String libelle;
    private final String description;
    /** Vrai si le rapport accepte une selection de camions. */
    private final boolean selectionCamions;

    TypeRapportPdf(String libelle, String description, boolean selectionCamions) {
        this.libelle = libelle;
        this.description = description;
        this.selectionCamions = selectionCamions;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSelectionCamions() {
        return selectionCamions;
    }

    /** Les rapports larges s'editent en paysage pour tenir toutes les colonnes. */
    public boolean paysage() {
        return this == PARC_GLOBAL || this == RECETTES || this == DEPENSES || this == MAINTENANCE;
    }

    public String nomFichier(LocalDate debut, LocalDate fin) {
        return "%s_%s_%s.pdf".formatted(name().toLowerCase(Locale.ROOT), debut, fin);
    }
}
