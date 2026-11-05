package tg.parcauto.api.pointdevente.materiaux;

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
import tg.parcauto.api.pointdevente.vente.LigneVente;

import java.math.BigDecimal;

/** Quantite d'une ligne de vente remise par un bon de livraison. */
@Entity
@Table(name = "pdv_ligne_bon_livraison")
@Getter
@Setter
@NoArgsConstructor
public class LigneBonLivraison extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bon_id", nullable = false)
    private BonLivraison bon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ligne_vente_id", nullable = false)
    private LigneVente ligneVente;

    /** Quantite remise, dans l'unite de vente de la ligne. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantite;

    /** Quantite sortie du stock, dans l'unite de base du produit. */
    @Column(name = "quantite_base", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantiteBase;

    /** Reste a livrer juste apres ce bon : le document reimprime ne change pas ensuite. */
    @Column(name = "reste_apres", nullable = false, precision = 14, scale = 3)
    private BigDecimal resteApres;
}
