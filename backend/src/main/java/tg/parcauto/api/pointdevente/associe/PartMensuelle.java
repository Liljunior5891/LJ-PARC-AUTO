package tg.parcauto.api.pointdevente.associe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Part d'un associe sur un mois clos, figee a la cloture : ce que l'entreprise lui doit pour ce mois. */
@Entity
@Table(name = "pdv_associe_mois")
@Getter
@Setter
@NoArgsConstructor
public class PartMensuelle extends BaseEntity {

    @Column(name = "associe_id", nullable = false)
    private Long associeId;

    /** Premier jour du mois. */
    @Column(nullable = false)
    private LocalDate mois;

    @Column(name = "chiffre_affaires", nullable = false, precision = 16, scale = 2)
    private BigDecimal chiffreAffaires;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal marge;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal part;
}
