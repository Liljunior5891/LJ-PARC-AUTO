package tg.parcauto.api.agence;

import tg.parcauto.api.security.Role;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Droits du module Agences. L'administrateur de l'entreprise les a tous ; ceux
 * du superviseur et du caissier se reglent par l'administrateur (ecran Reglages
 * et droits). Les valeurs par defaut suivent le cahier des charges.
 */
public enum PermissionAgence {

    TABLEAU_BORD("Voir le tableau de bord de son agence", "Soldes",
            EnumSet.of(Role.SUPERVISEUR_AGENCE, Role.CAISSIER_AGENCE)),
    SOLDES_CONSULTER("Consulter les soldes et la tresorerie", "Soldes",
            EnumSet.of(Role.SUPERVISEUR_AGENCE)),
    TRANSACTION_SAISIR("Saisir une transaction", "Operations",
            EnumSet.of(Role.SUPERVISEUR_AGENCE, Role.CAISSIER_AGENCE)),
    COMMISSION_MODIFIER("Modifier la commission proposee par le bareme (trace)", "Operations",
            EnumSet.of(Role.SUPERVISEUR_AGENCE)),
    COMMISSION_RAPPROCHER("Rapprocher les commissions avec les versements des operateurs", "Tresorerie",
            EnumSet.of(Role.SUPERVISEUR_AGENCE)),
    HISTORIQUE_COMPLET("Voir les operations de tous les caissiers", "Operations",
            EnumSet.of(Role.SUPERVISEUR_AGENCE)),
    TRANSACTION_ANNULER("Annuler une transaction validee (avec motif)", "Operations",
            EnumSet.of(Role.SUPERVISEUR_AGENCE)),
    DEPENSE_SAISIR("Saisir une depense (caissier : dans la limite du plafond)", "Operations",
            EnumSet.of(Role.SUPERVISEUR_AGENCE, Role.CAISSIER_AGENCE)),
    CAISSE_GERER("Ouvrir et fermer les caisses des caissiers (billettage)", "Tresorerie",
            EnumSet.of(Role.SUPERVISEUR_AGENCE)),
    DEPENSE_ANNULER("Annuler une depense (avec motif)", "Operations",
            EnumSet.of(Role.SUPERVISEUR_AGENCE)),
    DEPENSE_VALIDER("Viser une depense payee par la caisse (avant l'administrateur)", "Operations",
            EnumSet.of(Role.SUPERVISEUR_AGENCE)),
    CREDIT_GERER("Enregistrer les credits, dettes, apports et remboursements", "Tresorerie",
            EnumSet.noneOf(Role.class)),
    CARTE_STOCK("Entrer des cartes prepayees en stock (superviseur et administrateur seulement)", "Tresorerie",
            EnumSet.of(Role.SUPERVISEUR_AGENCE)),
    MOUVEMENT_SAISIR("Mouvements entre comptes (approvisionnement, banque...)", "Tresorerie",
            EnumSet.of(Role.SUPERVISEUR_AGENCE)),
    MOUVEMENT_VALIDER("Valider un mouvement au-dela du seuil", "Tresorerie",
            EnumSet.of(Role.SUPERVISEUR_AGENCE)),
    CLOTURE_SOUMETTRE("Soumettre la cloture de caisse", "Cloture",
            EnumSet.of(Role.SUPERVISEUR_AGENCE, Role.CAISSIER_AGENCE)),
    CLOTURE_VALIDER("Valider la cloture de caisse", "Cloture",
            EnumSet.of(Role.SUPERVISEUR_AGENCE)),
    SOLDES_INITIAUX("Saisir les soldes d'ouverture", "Administration",
            EnumSet.noneOf(Role.class)),
    PARAMETRAGE("Modifier les services, operations et baremes", "Administration",
            EnumSet.noneOf(Role.class)),
    AGENCES_GERER("Creer les agences et y affecter le personnel", "Administration",
            EnumSet.noneOf(Role.class)),
    AUDIT_CONSULTER("Consulter le journal d'audit", "Administration",
            EnumSet.of(Role.SUPERVISEUR_AGENCE)),
    RAPPORTS_COMPLETS("Rapports financiers complets", "Administration",
            EnumSet.noneOf(Role.class));

    /** Profils dont l'administrateur regle les droits. */
    public static final List<Role> PROFILS_REGLABLES = List.of(Role.SUPERVISEUR_AGENCE, Role.CAISSIER_AGENCE);

    private final String libelle;
    private final String groupe;
    private final Set<Role> parDefaut;

    PermissionAgence(String libelle, String groupe, Set<Role> parDefaut) {
        this.libelle = libelle;
        this.groupe = groupe;
        this.parDefaut = parDefaut;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getGroupe() {
        return groupe;
    }

    public Set<Role> getParDefaut() {
        return parDefaut;
    }
}
