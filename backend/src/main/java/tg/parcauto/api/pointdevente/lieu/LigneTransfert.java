package tg.parcauto.api.pointdevente.lieu;

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

/** Article transfere, en unite de base : demande, puis expedie et recu reellement. */
@Entity
@Table(name = "pdv_ligne_transfert")
@Getter
@Setter
@NoArgsConstructor
public class LigneTransfert extends BaseEntity {

    /** Ce que le gerant conclut d'un ecart a la reception. */
    public enum DecisionEcart { PERTE, RETOUR_SOURCE, ARRIVE }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfert_id", nullable = false)
    private Transfert transfert;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantite;

    @Column(name = "quantite_expediee", precision = 14, scale = 3)
    private BigDecimal quantiteExpediee;

    @Column(name = "quantite_recue", precision = 14, scale = 3)
    private BigDecimal quantiteRecue;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_ecart", length = 20)
    private DecisionEcart decisionEcart;

    /** Expedie mais pas arrive : encore en transit tant que le gerant n'a pas tranche. */
    public BigDecimal ecart() {
        return quantiteExpediee == null || quantiteRecue == null ? BigDecimal.ZERO : quantiteExpediee.subtract(quantiteRecue);
    }
}
