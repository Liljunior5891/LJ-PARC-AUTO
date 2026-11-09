package tg.parcauto.api.finance.depense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/** Categorie d'une charge generale du parc (loyer, salaires du bureau...), modifiable par l'administrateur. */
@Entity
@Table(name = "parc_categorie_charge")
@Getter
@Setter
@NoArgsConstructor
public class CategorieCharge extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(length = 16)
    private String icone;

    @Column(nullable = false)
    private int ordre;

    @Column(nullable = false)
    private boolean actif = true;
}
