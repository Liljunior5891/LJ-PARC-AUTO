package tg.parcauto.api.pointdevente.materiaux;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.math.BigDecimal;

/**
 * Reglages du point de vente « Quincaillerie & materiaux », choisis par
 * l'administrateur ; une ligne par entreprise, creee a la premiere
 * modification. Sans ligne, les valeurs par defaut ci-dessous s'appliquent.
 */
@Entity
@Table(name = "pdv_reglage_materiaux")
@Getter
@Setter
@NoArgsConstructor
public class ReglageMateriaux extends BaseEntity {

    /** Vente a credit et acomptes autorises. */
    @Column(name = "credit_actif", nullable = false)
    private boolean creditActif = true;

    /** Le caissier peut vendre a credit, dans la limite du plafond ; sinon gerant et administrateur seulement. */
    @Column(name = "credit_caissier", nullable = false)
    private boolean creditCaissier = true;

    /** Gerant et administrateur peuvent depasser le plafond ou passer outre un retard de paiement. */
    @Column(name = "depassement_gerant", nullable = false)
    private boolean depassementGerant = true;

    /** Pas de nouveau credit pour un client dont une echeance est depassee. */
    @Column(name = "bloquer_si_retard", nullable = false)
    private boolean bloquerSiRetard = true;

    /** Plafond de credit des clients qui n'en ont pas un a eux ; nul : sans limite. */
    @Column(name = "plafond_defaut", precision = 16, scale = 2)
    private BigDecimal plafondDefaut;

    /** Delai de paiement d'une vente a credit, en jours, sauf delai propre au client. */
    @Column(name = "delai_paiement_jours", nullable = false)
    private int delaiPaiementJours = 30;

    /** Acompte minimum d'une vente a credit, en pourcentage du total (0 : aucun). */
    @Column(name = "acompte_minimum", nullable = false)
    private int acompteMinimum;

    /** Chaque vente doit etre rattachee a une fiche client. */
    @Column(name = "client_obligatoire", nullable = false)
    private boolean clientObligatoire;

    @Column(name = "devis_actif", nullable = false)
    private boolean devisActif = true;

    @Column(name = "devis_validite_jours", nullable = false)
    private int devisValiditeJours = 30;

    /** Conditions imprimees au pied des devis (livraison, paiement...). */
    @Column(name = "mentions_devis", length = 500)
    private String mentionsDevis;

    /** Facture A4 proposee apres chaque vente et depuis l'historique. */
    @Column(name = "facture_a4", nullable = false)
    private boolean factureA4 = true;

    /** Les prix sont TTC : la facture en detaille la TVA au taux de la societe. Faux : « TVA non applicable ». */
    @Column(name = "tva_active", nullable = false)
    private boolean tvaActive = true;

    /** Vente au sac, a la tonne, a la botte ou au carton. */
    @Column(name = "conditionnements_actifs", nullable = false)
    private boolean conditionnementsActifs = true;

    /** Le client peut emporter une partie de sa commande et revenir chercher le reste. */
    @Column(name = "livraison_differee", nullable = false)
    private boolean livraisonDifferee = true;

    /** Vendre plus que le disponible : le surplus reste a livrer a l'arrivage de la marchandise. */
    @Column(name = "vente_sur_arrivage", nullable = false)
    private boolean venteSurArrivage;
}
