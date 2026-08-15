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
import tg.parcauto.api.exploitation.location.Location;
import tg.parcauto.api.exploitation.mission.Mission;
import tg.parcauto.api.finance.commun.ModePaiement;
import tg.parcauto.api.parc.camion.Camion;
import tg.parcauto.api.parc.chauffeur.Chauffeur;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Depense rattachee a un camion, chapitres 16 et 18 du cahier des charges.
 * Les colonnes station, quantite et prix au litre ne sont renseignees que
 * pour les pleins de carburant.
 */
@Entity
@Table(name = "depense")
@Getter
@Setter
@NoArgsConstructor
public class Depense extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "camion_id", nullable = false)
    private Camion camion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chauffeur_id")
    private Chauffeur chauffeur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id")
    private Mission mission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CategorieDepense categorie;

    @Enumerated(EnumType.STRING)
    @Column(name = "sous_categorie", nullable = false, length = 40)
    private SousCategorieDepense sousCategorie;

    @Column(nullable = false, length = 255)
    private String libelle;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal montant;

    @Column(name = "date_depense", nullable = false)
    private LocalDate dateDepense;

    @Column(length = 180)
    private String fournisseur;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_paiement", length = 25)
    private ModePaiement modePaiement;

    @Column(length = 80)
    private String reference;

    @Column(length = 500)
    private String justificatif;

    /** Compteur du camion au moment de la depense, indispensable au calcul de consommation. */
    @Column
    private Long kilometrage;

    @Column(length = 150)
    private String station;

    @Column(name = "quantite_litres", precision = 12, scale = 2)
    private BigDecimal quantiteLitres;

    @Column(name = "prix_litre", precision = 12, scale = 2)
    private BigDecimal prixLitre;

    /**
     * Depense refacturee au client : elle reste tracee sur le camion mais ne
     * greve pas son resultat (chapitre 11, repartition des depenses).
     */
    @Column(name = "a_charge_client", nullable = false)
    private boolean aChargeClient = false;

    @Column(columnDefinition = "text")
    private String observations;

    /** Aligne la categorie sur la nature choisie. */
    public void synchroniserCategorie() {
        if (sousCategorie != null) {
            this.categorie = sousCategorie.getCategorie();
        }
    }

    public boolean estPleinCarburant() {
        return categorie == CategorieDepense.CARBURANT && quantiteLitres != null;
    }

    /** Prix au litre, deduit du montant si le prix unitaire n'a pas ete saisi. */
    public BigDecimal prixLitreCalcule() {
        if (prixLitre != null) {
            return prixLitre;
        }
        if (quantiteLitres == null || quantiteLitres.signum() == 0) {
            return null;
        }
        return montant.divide(quantiteLitres, 2, RoundingMode.HALF_UP);
    }

    /** Montant supporte par l'entreprise : zero si la depense est refacturee au client. */
    public BigDecimal montantACharge() {
        return aChargeClient ? BigDecimal.ZERO : montant;
    }
}
