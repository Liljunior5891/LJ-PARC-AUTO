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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Une arrivee de marchandise sur une commande fournisseur, a sa date : une commande
 * recue en plusieurs fois compte ses achats au mois de chaque reception.
 */
@Entity
@Table(name = "pdv_reception_commande")
@Getter
@Setter
@NoArgsConstructor
public class ReceptionCommande extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commande_id", nullable = false)
    private CommandeFournisseur commande;

    @Column(name = "recue_le", nullable = false)
    private Instant recueLe;

    @Column(name = "recue_par", length = 150)
    private String recuePar;

    /** Valeur de la marchandise arrivee a cette reception (quantite x cout). */
    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal valeur = BigDecimal.ZERO;
}
