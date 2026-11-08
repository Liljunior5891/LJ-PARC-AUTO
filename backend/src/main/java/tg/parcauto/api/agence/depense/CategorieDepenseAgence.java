package tg.parcauto.api.agence.depense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/** Categorie de depense des agences (loyer, electricite, salaires...), commune a toute l'entreprise. */
@Entity
@Table(name = "agc_categorie_depense")
@Getter
@Setter
@NoArgsConstructor
public class CategorieDepenseAgence extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(nullable = false)
    private int ordre;

    @Column(nullable = false)
    private boolean actif = true;
}
