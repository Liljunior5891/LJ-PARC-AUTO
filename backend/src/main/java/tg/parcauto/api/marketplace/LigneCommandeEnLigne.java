package tg.parcauto.api.marketplace;

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

import java.math.BigDecimal;

/** Ligne d'une commande en ligne : libelle et prix figes au moment de la commande. */
@Entity
@Table(name = "mkt_ligne_commande")
@Getter
@Setter
@NoArgsConstructor
public class LigneCommandeEnLigne extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commande_id", nullable = false)
    private CommandeEnLigne commande;

    @Column(name = "offre_id")
    private Long offreId;

    /** Article du point de vente dont le stock a ete sorti, s'il y en a un. */
    @Column(name = "article_id")
    private Long articleId;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(name = "prix_unitaire", nullable = false, precision = 16, scale = 2)
    private BigDecimal prixUnitaire;

    @Column(nullable = false)
    private int quantite;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal total;
}
