package tg.parcauto.api.common.notification;

import lombok.Getter;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;

/**
 * Notifications envoyees a l'administrateur d'une entreprise, par WhatsApp au
 * numero donne a l'inscription et par e-mail aux administrateurs. Chaque type se
 * choisit dans Administration, Notifications ; sans choix, le reglage par defaut
 * s'applique. Un type lie a un module n'est propose que si le module est actif.
 */
@Getter
public enum TypeAlerte {

    COMPTE("Compte et securite",
            "Bienvenue a l'inscription, nouvel utilisateur cree, module active ou retire.",
            null, true, false),
    ABONNEMENT("Abonnement",
            "Fin d'essai, echeance proche, delai de grace, compte en lecture seule, paiement valide ou rejete.",
            null, true, true),
    VALIDATION("Validations en attente",
            "Depense ou sortie de caisse qui attend la decision de l'administrateur.",
            null, true, false),
    ALERTE_STOCK("Alertes de stock",
            "Articles passes sous le seuil d'alerte ou en rupture (chaque article une fois par jour au plus).",
            ModuleApplicatif.POINT_DE_VENTE, true, false),
    ECART_CAISSE("Ecarts de caisse",
            "Caisse fermee avec un manquant ou un excedent.",
            null, true, false),
    VENTE_HORS_LIGNE("Ventes hors ligne a traiter",
            "Vente faite sans connexion arrivee en conflit.",
            ModuleApplicatif.POINT_DE_VENTE, true, false),
    COMMANDE_EN_LIGNE("Commandes en ligne",
            "Nouvelle commande ou demande de location dans la boutique en ligne.",
            ModuleApplicatif.MARKETPLACE, true, false),
    ALERTE_GPS("Alertes GPS critiques",
            "Exces de vitesse, sortie de zone et autres alertes critiques des vehicules.",
            ModuleApplicatif.GESTION_PARC, true, false),
    ECHEANCES_PARC("Echeances du parc",
            "Documents des vehicules et des chauffeurs qui expirent dans les 7 jours ou ont expire (chaque matin).",
            ModuleApplicatif.GESTION_PARC, true, false),
    RESUME_JOURNALIER("Resume de la journee",
            "Chaque soir a 20 h : chiffre d'affaires, nombre de ventes et depenses du jour.",
            ModuleApplicatif.POINT_DE_VENTE, false, false);

    private final String libelle;
    private final String description;
    /** Module requis ; nul : toujours propose. */
    private final ModuleApplicatif module;
    private final boolean whatsappParDefaut;
    private final boolean emailParDefaut;

    /**
     * Rappels de l'abonnement (echeance, grace, lecture seule, recu) : l'e-mail part
     * toujours, l'administrateur ne peut couper que WhatsApp.
     */
    public boolean isEmailObligatoire() {
        return this == ABONNEMENT;
    }

    TypeAlerte(String libelle, String description, ModuleApplicatif module, boolean whatsappParDefaut,
               boolean emailParDefaut) {
        this.libelle = libelle;
        this.description = description;
        this.module = module;
        this.whatsappParDefaut = whatsappParDefaut;
        this.emailParDefaut = emailParDefaut;
    }
}
