package tg.parcauto.api.pointdevente.vente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;
import tg.parcauto.api.pointdevente.catalogue.Article;

import java.math.BigDecimal;

/**
 * Ligne de ticket. Libelle et prix sont figes au moment de la vente : changer
 * l'article ensuite ne reecrit pas les tickets deja emis.
 */
@Entity
@Table(name = "pdv_ligne_vente")
@Getter
@Setter
@NoArgsConstructor
public class LigneVente extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vente_id", nullable = false)
    private Vente vente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(name = "prix_unitaire", nullable = false, precision = 16, scale = 2)
    private BigDecimal prixUnitaire;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantite;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal remise = BigDecimal.ZERO;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal total;

    /** Cout d'achat unitaire au moment de la vente, pour une marge qui ne varie plus ensuite. */
    @Column(name = "prix_achat", precision = 16, scale = 2)
    private BigDecimal prixAchat;
}
