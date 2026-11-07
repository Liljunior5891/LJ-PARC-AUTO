package tg.parcauto.api.pointdevente.reglage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.math.BigDecimal;

/**
 * Options du point de vente qui ne conviennent pas a tous les commerces,
 * desactivees par defaut et reglees par l'administrateur.
 */
@Entity
@Table(name = "pdv_reglage")
@Getter
@Setter
@NoArgsConstructor
public class ReglagePdv extends BaseEntity {

    /** Benefices des associes : profil ASSOCIE et part de la marge de ses produits. */
    @Column(name = "associes_actif", nullable = false)
    private boolean associesActif;

    /** Remise du caissier au gerant, coffre du gerant, degagement vers l'administrateur. */
    @Column(name = "circuit_caisse_actif", nullable = false)
    private boolean circuitCaisseActif;

    /** Ecart (en valeur absolue) au-dela duquel une justification est exigee. */
    @Column(name = "ecart_tolere", nullable = false, precision = 16, scale = 2)
    private BigDecimal ecartTolere = BigDecimal.ZERO;
}
