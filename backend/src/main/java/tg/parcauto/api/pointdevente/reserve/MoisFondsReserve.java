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
 * Versement d'un mois termine au fonds de reserve, fige a la cloture : une
 * correction ulterieure (vente annulee, depense antidatee) ne le change plus.
 */
@Entity
@Table(name = "pdv_fonds_reserve_mois")
@Getter
@Setter
@NoArgsConstructor
public class MoisFondsReserve extends BaseEntity {

    @Column(nullable = false)
    private LocalDate debut;

    @Column(nullable = false)
    private LocalDate fin;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal pourcent;

    @Column(name = "chiffre_affaires", nullable = false, precision = 16, scale = 2)
    private BigDecimal chiffreAffaires;

    @Column(name = "cout_achats", nullable = false, precision = 16, scale = 2)
    private BigDecimal coutAchats;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal depenses;

    @Column(name = "benefice_net", nullable = false, precision = 16, scale = 2)
    private BigDecimal beneficeNet;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal dotation;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal verse;
}
