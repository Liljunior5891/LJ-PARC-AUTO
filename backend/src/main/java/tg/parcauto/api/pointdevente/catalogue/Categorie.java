package tg.parcauto.api.pointdevente.catalogue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/** Rayon d'articles : il organise les boutons de l'ecran de caisse. */
@Entity
@Table(name = "pdv_categorie")
@Getter
@Setter
@NoArgsConstructor
public class Categorie extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String nom;

    /** Couleur des boutons de la caisse, au format #rrggbb. */
    @Column(length = 9)
    private String couleur;

    @Column(nullable = false)
    private int ordre;

    @Column(nullable = false)
    private boolean actif = true;

    /** Pictogramme (emoji) affiche sur la carte de la categorie a la caisse. */
    @Column(length = 16)
    private String icone;

    /** En restauration : ecran (cuisine ou bar) qui recoit les articles de la categorie. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostePreparation poste = PostePreparation.AUCUN;
}
