package tg.parcauto.api.pointdevente.restauration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/** Espace du restaurant (salle, terrasse, comptoir) et son plan de tables. */
@Entity
@Table(name = "pdv_salle")
@Getter
@Setter
@NoArgsConstructor
public class Salle extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(nullable = false)
    private int ordre;

    @Column(nullable = false)
    private boolean actif = true;
}
