package tg.parcauto.api.pointdevente.approvisionnement;

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

/** Produit commande : quantite et cout prevus, puis quantite effectivement recue. */
@Entity
@Table(name = "pdv_ligne_commande_fournisseur")
@Getter
@Setter
@NoArgsConstructor
public class LigneCommandeFournisseur extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commande_id", nullable = false)
    private CommandeFournisseur commande;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantite;

    @Column(name = "cout_unitaire", precision = 16, scale = 2)
    private BigDecimal coutUnitaire;

    @Column(name = "quantite_recue", precision = 14, scale = 3)
    private BigDecimal quantiteRecue;

    public BigDecimal total() {
        return coutUnitaire == null ? BigDecimal.ZERO : coutUnitaire.multiply(quantite);
    }
}
