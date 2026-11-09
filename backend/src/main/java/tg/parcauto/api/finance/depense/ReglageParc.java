package tg.parcauto.api.finance.depense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/** Reglages de la gestion de parc, modifies par l'administrateur. Sans ligne, tout est desactive. */
@Entity
@Table(name = "parc_reglage")
@Getter
@Setter
@NoArgsConstructor
public class ReglageParc extends BaseEntity {

    /** Une depense saisie par le gestionnaire attend l'administrateur avant de compter. */
    @Column(name = "validation_depenses", nullable = false)
    private boolean validationDepenses;
}
