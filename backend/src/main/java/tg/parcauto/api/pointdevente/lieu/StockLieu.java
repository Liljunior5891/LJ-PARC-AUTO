package tg.parcauto.api.pointdevente.lieu;

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

/** Stock d'un article dans un lieu, dans l'unite de base du produit. */
@Entity
@Table(name = "pdv_stock_lieu")
@Getter
@Setter
@NoArgsConstructor
public class StockLieu extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lieu_id", nullable = false)
    private LieuStock lieu;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal stock = BigDecimal.ZERO;

    /** Vendu et a retirer ici : encore sur place, mais plus disponible. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal reserve = BigDecimal.ZERO;

    @Column(name = "seuil_alerte", precision = 14, scale = 3)
    private BigDecimal seuilAlerte;

    public BigDecimal disponible() {
        return stock.subtract(reserve);
    }
}
