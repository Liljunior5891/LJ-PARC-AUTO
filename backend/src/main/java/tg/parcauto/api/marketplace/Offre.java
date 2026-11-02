package tg.parcauto.api.marketplace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.math.BigDecimal;

/**
 * Offre publiee dans la boutique. Un article du point de vente garde son prix,
 * sa photo et son stock en direct (sauf prix ou photo propres a la boutique) ;
 * un vehicule se loue a la journee ; un produit libre porte tout lui-meme.
 */
@Entity
@Table(name = "mkt_offre")
@Getter
@Setter
@NoArgsConstructor
public class Offre extends BaseEntity {

    public enum Type {
        /** Article du point de vente. */
        ARTICLE,
        /** Vehicule du parc, propose a la location. */
        VEHICULE,
        /** Produit saisi directement dans la Marketplace. */
        LIBRE
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "camion_id")
    private Long camionId;

    @Column(nullable = false, length = 150)
    private String titre;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 80)
    private String categorie;

    /** Article : vide = prix de vente de l'article. Vehicule : tarif par jour. */
    @Column(precision = 16, scale = 2)
    private BigDecimal prix;

    @Column(name = "unite_prix", length = 20)
    private String unitePrix;

    /** Vehicule : caution demandee. */
    @Column(precision = 16, scale = 2)
    private BigDecimal caution;

    /** Vide = photo de l'article ou du vehicule. */
    @Column(columnDefinition = "text")
    private String image;

    /** Produit libre : quantite disponible ; vide = sans suivi de stock. */
    private Integer stock;

    @Column(nullable = false)
    private boolean publiee = true;

    @Column(name = "mise_en_avant", nullable = false)
    private boolean miseEnAvant;

    @Column(nullable = false)
    private int ordre;
}
