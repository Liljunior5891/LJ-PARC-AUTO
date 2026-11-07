package tg.parcauto.api.pointdevente.depense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/** Categorie de depense du point de vente (salaires, loyer, electricite...), modifiable par l'administrateur. */
@Entity(name = "CategorieDepensePdv")
@Table(name = "pdv_categorie_depense")
@Getter
@Setter
@NoArgsConstructor
public class CategorieDepensePdv extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(length = 16)
    private String icone;

    @Column(nullable = false)
    private int ordre;

    @Column(nullable = false)
    private boolean actif = true;
}
