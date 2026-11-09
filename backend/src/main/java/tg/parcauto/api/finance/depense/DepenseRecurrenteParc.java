package tg.parcauto.api.finance.depense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;
import tg.parcauto.api.finance.commun.ModePaiement;
import tg.parcauto.api.parc.camion.Camion;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Modele d'une depense qui revient chaque mois (loyer, salaire, assurance d'un
 * vehicule, abonnement) : propose dans « A payer » jusqu'a son paiement.
 * Soit un vehicule et sa nature, soit une categorie de charge generale.
 */
@Entity
@Table(name = "parc_depense_recurrente")
@Getter
@Setter
@NoArgsConstructor
public class DepenseRecurrenteParc extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String libelle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camion_id")
    private Camion camion;

    @Enumerated(EnumType.STRING)
    @Column(name = "sous_categorie", length = 40)
    private SousCategorieDepense sousCategorie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_charge_id")
    private CategorieCharge categorieCharge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fournisseur_id")
    private FournisseurParc fournisseur;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal montant;

    @Column(name = "jour_echeance", nullable = false)
    private int jourEcheance = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_paiement", length = 25)
    private ModePaiement modePaiement;

    /** Premier mois du (1er jour du mois). */
    @Column(nullable = false)
    private LocalDate debut;

    /** Dernier mois du, vide si sans fin. */
    @Column
    private LocalDate fin;

    @Column(nullable = false)
    private boolean actif = true;

    @Column(length = 255)
    private String note;
}
