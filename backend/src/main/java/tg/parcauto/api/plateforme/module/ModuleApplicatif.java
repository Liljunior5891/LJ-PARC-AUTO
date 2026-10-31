package tg.parcauto.api.plateforme.module;

import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.security.Role;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Catalogue des modules de la plateforme.
 * <p>
 * Chaque entreprise active ceux dont elle a besoin. Un module encore en
 * preparation figure au catalogue pour etre annonce, mais ne peut pas etre
 * active tant que {@code disponible} est faux.
 * <p>
 * Chaque module nomme aussi les profils qui y ont acces : un caissier ne voit
 * pas le parc, un chauffeur ne voit pas la caisse, meme si l'entreprise a
 * active les deux. L'administrateur de l'entreprise accede a tout.
 * <p>
 * Un module peut se decliner en variantes, choisies par l'entreprise a
 * l'activation : le point de vente d'une boutique n'a pas les ecrans d'un
 * restaurant (tables, commandes, cuisine). La premiere variante est celle par
 * defaut.
 * <p>
 * Ajouter un module : une constante ici, puis {@link ModuleRequis} sur ses
 * controleurs et une entree dans le catalogue de l'interface.
 */
public enum ModuleApplicatif {

    GESTION_PARC(
            "Gestion de parc",
            "Vehicules, chauffeurs, locations, missions, finances, GPS, maintenance et pilotage de la rentabilite.",
            true,
            EnumSet.of(Role.ADMIN, Role.GESTIONNAIRE_PARC, Role.CHAUFFEUR),
            List.of()),
    POINT_DE_VENTE(
            "Point de vente",
            "Caisse, articles et codes-barres, stock, commandes, sessions de caisse et chiffre d'affaires du jour.",
            true,
            EnumSet.of(Role.ADMIN, Role.GERANT, Role.CAISSIER, Role.SERVEUR, Role.PREPARATEUR),
            List.of(
                    new Variante("BOUTIQUE", "Boutique / commerce",
                            "Caisse rapide au comptoir : douchette, panier et recu."),
                    new Variante("RESTAURATION", "Bar, restaurant, cafe",
                            "Plan de salle, commandes par table, ecrans cuisine et bar, additions."))),
    MARKETPLACE(
            "Marketplace",
            "Publication de vos offres et reception de commandes en ligne.",
            false,
            EnumSet.of(Role.ADMIN),
            List.of());

    /** Declinaison d'un module, choisie par l'entreprise. */
    public record Variante(String code, String libelle, String description) {
    }

    private final String libelle;
    private final String description;
    private final boolean disponible;
    private final Set<Role> profils;
    private final List<Variante> variantes;

    ModuleApplicatif(String libelle, String description, boolean disponible, Set<Role> profils,
                     List<Variante> variantes) {
        this.libelle = libelle;
        this.description = description;
        this.disponible = disponible;
        this.profils = profils;
        this.variantes = variantes;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDisponible() {
        return disponible;
    }

    /** Profils autorises a travailler dans le module. */
    public Set<Role> getProfils() {
        return profils;
    }

    public List<Variante> getVariantes() {
        return variantes;
    }

    /** Variante retenue si l'entreprise n'en choisit pas ; vide pour un module sans variante. */
    public Optional<String> varianteParDefaut() {
        return variantes.stream().findFirst().map(Variante::code);
    }

    /**
     * Valide la variante demandee. Nulle, c'est la variante par defaut ; un
     * module sans variante n'en accepte aucune.
     */
    public String varianteValide(String demandee) {
        if (demandee == null || demandee.isBlank()) {
            return varianteParDefaut().orElse(null);
        }
        return variantes.stream()
                .map(Variante::code)
                .filter(code -> code.equals(demandee.trim()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Type d'activite inconnu pour le module " + libelle + " : " + demandee));
    }
}
