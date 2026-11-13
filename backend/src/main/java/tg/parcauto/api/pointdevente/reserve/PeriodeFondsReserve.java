package tg.parcauto.api.pointdevente.reserve;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Periode pendant laquelle un pourcentage du benefice net alimente le fonds de
 * reserve : ouverte a l'activation ou au changement de taux, fermee a la
 * desactivation. Les jours passes gardent le taux qui s'appliquait alors.
 */
@Entity
@Table(name = "pdv_fonds_reserve_periode")
@Getter
@Setter
@NoArgsConstructor
public class PeriodeFondsReserve extends BaseEntity {

    @Column(nullable = false)
    private LocalDate debut;

    /** Dernier jour compris ; vide tant que la periode est en cours. */
    private LocalDate fin;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal pourcent;
}
