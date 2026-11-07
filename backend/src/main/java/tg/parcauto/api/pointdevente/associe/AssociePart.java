package tg.parcauto.api.pointdevente.associe;

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
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.Categorie;

import java.math.BigDecimal;

/**
 * Rattachement d'un associe a un produit, ou a une categorie entiere, avec son
 * pourcentage de la marge brute. Sur un produit, le rattachement direct
 * l'emporte sur celui de sa categorie.
 */
@Entity
@Table(name = "pdv_associe_part")
@Getter
@Setter
@NoArgsConstructor
public class AssociePart extends BaseEntity {

    /** Compte utilisateur au profil ASSOCIE. */
    @Column(name = "associe_id", nullable = false)
    private Long associeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id")
    private Article article;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id")
    private Categorie categorie;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal taux;
}
