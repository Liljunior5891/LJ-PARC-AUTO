package tg.parcauto.api.pointdevente.stock;

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
import tg.parcauto.api.pointdevente.catalogue.Article;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Trace d'une variation de stock. La quantite est signee (positive en entree,
 * negative en sortie) et le stock resultant est conserve : l'historique d'un
 * article se lit sans recalcul.
 */
@Entity
@Table(name = "pdv_mouvement_stock")
@Getter
@Setter
@NoArgsConstructor
public class MouvementStock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeMouvementStock type;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantite;

    @Column(name = "stock_apres", nullable = false, precision = 14, scale = 3)
    private BigDecimal stockApres;

    @Column(name = "cout_unitaire", precision = 16, scale = 2)
    private BigDecimal coutUnitaire;

    @Column(name = "date_mouvement", nullable = false)
    private Instant dateMouvement;

    @Column(length = 255)
    private String motif;

    /** Vente a l'origine du mouvement, pour une vente ou son annulation. */
    @Column(name = "vente_id")
    private Long venteId;
}
