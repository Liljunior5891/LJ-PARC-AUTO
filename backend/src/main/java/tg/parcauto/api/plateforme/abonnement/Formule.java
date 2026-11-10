package tg.parcauto.api.plateforme.abonnement;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.EntiteAuditee;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Formule d'abonnement : un prix mensuel par module et des limites de volume.
 * L'entreprise paie la somme des modules qu'elle choisit, au prix de sa formule.
 * Une limite nulle signifie « illimite ».
 */
@Entity
@Table(name = "abo_formule")
@Getter
@Setter
@NoArgsConstructor
public class Formule extends EntiteAuditee {

    @Column(nullable = false, length = 60)
    private String nom;

    @Column(length = 300)
    private String description;

    /** Prix mensuel de chaque module dans cette formule. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "abo_formule_prix", joinColumns = @JoinColumn(name = "formule_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "module", length = 40)
    @Column(name = "prix_mensuel", nullable = false)
    private Map<ModuleApplicatif, BigDecimal> prix = new EnumMap<>(ModuleApplicatif.class);

    @Column(name = "max_utilisateurs")
    private Integer maxUtilisateurs;

    @Column(name = "max_vehicules")
    private Integer maxVehicules;

    @Column(name = "max_agences")
    private Integer maxAgences;

    @Column(name = "max_caisses")
    private Integer maxCaisses;

    @Column(name = "max_employes")
    private Integer maxEmployes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "mise_en_avant", nullable = false)
    private boolean miseEnAvant;

    @Column(nullable = false)
    private int ordre;

    /** Prix mensuel du module dans la formule ; zero s'il n'est pas renseigne. */
    public BigDecimal prixDe(ModuleApplicatif module) {
        return prix.getOrDefault(module, BigDecimal.ZERO);
    }

    /** Limite de la ressource ; nulle = illimitee. */
    public Integer limite(Ressource r) {
        return switch (r) {
            case UTILISATEURS -> maxUtilisateurs;
            case VEHICULES -> maxVehicules;
            case AGENCES -> maxAgences;
            case CAISSES -> maxCaisses;
            case EMPLOYES -> maxEmployes;
        };
    }
}
