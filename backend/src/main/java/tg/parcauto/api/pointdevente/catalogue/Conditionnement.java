package tg.parcauto.api.pointdevente.catalogue;

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

import java.math.BigDecimal;

/**
 * Autre facon de vendre un produit : la tonne de ciment (20 sacs), la botte de
 * fer (10 barres), le carton de carreaux (1,44 m2). Le stock reste tenu dans
 * l'unite de base du produit ; vendre un conditionnement en sort
 * {@code coefficient} unites.
 */
@Entity
@Table(name = "pdv_conditionnement")
@Getter
@Setter
@NoArgsConstructor
public class Conditionnement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(nullable = false, length = 40)
    private String nom;

    /** Nombre d'unites de base contenues. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal coefficient;

    @Column(name = "prix_vente", nullable = false, precision = 16, scale = 2)
    private BigDecimal prixVente;

    @Column(name = "code_barres", length = 60)
    private String codeBarres;

    @Column(nullable = false)
    private boolean actif = true;
}
