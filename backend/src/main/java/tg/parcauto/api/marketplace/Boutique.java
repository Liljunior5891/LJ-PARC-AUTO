package tg.parcauto.api.marketplace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.math.BigDecimal;

/**
 * Boutique en ligne de l'entreprise : une seule par entreprise, servie a
 * l'adresse publique /boutique/{slug} et listee sur la place de marche
 * commune une fois publiee.
 */
@Entity
@Table(name = "mkt_boutique")
@Getter
@Setter
@NoArgsConstructor
public class Boutique extends BaseEntity {

    /** Adresse publique, unique sur toute la plateforme (minuscules, chiffres, tirets). */
    @Column(nullable = false, length = 60)
    private String slug;

    @Column(nullable = false, length = 150)
    private String nom;

    @Column(length = 200)
    private String slogan;

    @Column(columnDefinition = "text")
    private String description;

    /** Rayon de la place de marche : alimentation, restauration, location... */
    @Column(length = 60)
    private String categorie;

    /** Image de bandeau (adresse ou image encodee). */
    @Column(columnDefinition = "text")
    private String couverture;

    @Column(length = 40)
    private String telephone;

    @Column(length = 40)
    private String whatsapp;

    @Column(length = 150)
    private String email;

    @Column(length = 255)
    private String adresse;

    @Column(length = 80)
    private String ville;

    @Column(length = 255)
    private String horaires;

    @Column(nullable = false)
    private boolean publiee;

    @Column(name = "retrait_actif", nullable = false)
    private boolean retraitActif = true;

    @Column(name = "livraison_active", nullable = false)
    private boolean livraisonActive;

    @Column(name = "frais_livraison", precision = 16, scale = 2)
    private BigDecimal fraisLivraison;

    @Column(name = "zone_livraison", length = 255)
    private String zoneLivraison;

    /** Paiement au retrait ou a la livraison. */
    @Column(name = "paiement_remise", nullable = false)
    private boolean paiementRemise = true;

    @Column(name = "paiement_mobile_money", nullable = false)
    private boolean paiementMobileMoney;

    @Column(name = "message_confirmation", length = 500)
    private String messageConfirmation;
}
