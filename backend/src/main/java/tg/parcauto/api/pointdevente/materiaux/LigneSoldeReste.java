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

/** Ce qui a ete retire d'une ligne de vente : assez pour imprimer l'avoir et pour l'annuler. */
@Entity
@Table(name = "pdv_ligne_solde_reste")
@Getter
@Setter
@NoArgsConstructor
public class LigneSoldeReste extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solde_id", nullable = false)
    private SoldeReste solde;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ligne_vente_id", nullable = false)
    private LigneVente ligneVente;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantite;

    @Column(name = "quantite_base", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantiteBase;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal remise;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal total;
}
