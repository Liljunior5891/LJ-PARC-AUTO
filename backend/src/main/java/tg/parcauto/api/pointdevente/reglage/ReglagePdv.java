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

    /**
     * Depenses et sorties d'especes payees par la caisse : validees par le gerant
     * puis par l'administrateur avant de sortir de la caisse et d'entrer dans les journaux.
     */
    @Column(name = "validation_depenses_caisse", nullable = false)
    private boolean validationDepensesCaisse;

    /** Minutes sans geste apres lesquelles l'ecran de caisse se verrouille (code PIN). */
    @Column(name = "minutes_verrou_caisse", nullable = false)
    private int minutesVerrouCaisse = 5;

    /** Remise maximale (en % du ticket) qu'un caissier accorde seul ; 100 : sans plafond. */
    @Column(name = "remise_max_caissier_pourcent", nullable = false, precision = 5, scale = 2)
    private BigDecimal remiseMaxCaissierPourcent = BigDecimal.TEN;
}
