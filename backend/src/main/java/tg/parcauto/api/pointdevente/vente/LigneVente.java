package tg.parcauto.api.pointdevente.vente;

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

import java.math.BigDecimal;

/**
 * Ligne de ticket. Libelle et prix sont figes au moment de la vente : changer
 * l'article ensuite ne reecrit pas les tickets deja emis.
 */
@Entity
@Table(name = "pdv_ligne_vente")
@Getter
@Setter
@NoArgsConstructor
public class LigneVente extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vente_id", nullable = false)
    private Vente vente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(name = "prix_unitaire", nullable = false, precision = 16, scale = 2)
    private BigDecimal prixUnitaire;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantite;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal remise = BigDecimal.ZERO;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal total;

    /** Cout d'achat unitaire au moment de la vente, pour une marge qui ne varie plus ensuite. */
    @Column(name = "prix_achat", precision = 16, scale = 2)
    private BigDecimal prixAchat;

    /** Conditionnement vendu (tonne, botte, carton) ; nul pour l'unite de base. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conditionnement_id")
    private tg.parcauto.api.pointdevente.catalogue.Conditionnement conditionnement;

    /** Quantite sortie du stock, dans l'unite de base du produit. */
    @Column(name = "quantite_base", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantiteBase;

    /** Quantite deja remise au client, dans l'unite de vente de la ligne. */
    @Column(name = "quantite_livree", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantiteLivree;

    /** Quantite deja sortie du stock, dans l'unite de base du produit. */
    @Column(name = "quantite_base_livree", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantiteBaseLivree;

    /** Sauf indication contraire, tout est remis a la vente. */
    @jakarta.persistence.PrePersist
    void completerQuantiteBase() {
        if (quantiteBase == null) {
            quantiteBase = quantite;
        }
        if (quantiteLivree == null) {
            quantiteLivree = quantite;
        }
        if (quantiteBaseLivree == null) {
            quantiteBaseLivree = quantiteBase;
        }
    }

    /** Reste a remettre, dans l'unite de vente de la ligne. */
    public BigDecimal resteALivrer() {
        return quantiteLivree == null ? BigDecimal.ZERO : quantite.subtract(quantiteLivree);
    }

    /** Reste a sortir du stock, dans l'unite de base du produit. */
    public BigDecimal resteBaseALivrer() {
        return quantiteBaseLivree == null ? BigDecimal.ZERO : quantiteBase.subtract(quantiteBaseLivree);
    }

    /** Sorti du stock jusqu'ici, dans l'unite de base : toute la ligne si rien n'a ete fixe. */
    public BigDecimal baseDejaLivree() {
        return quantiteBaseLivree == null ? quantiteBase : quantiteBaseLivree;
    }

    /** A l'encaissement : ce que le client emporte tout de suite, le reste etant a livrer. */
    public void fixerEmporte(BigDecimal emporte) {
        quantiteLivree = emporte;
        quantiteBaseLivree = enBase(emporte);
    }

    /**
     * Enregistre une remise et renvoie la quantite a sortir du stock. La derniere
     * remise sort exactement ce qui reste : les arrondis d'un conditionnement
     * (un tiers de tonne...) ne laissent pas de reliquat.
     */
    public BigDecimal remettre(BigDecimal remise) {
        BigDecimal base = remise.compareTo(resteALivrer()) == 0 ? resteBaseALivrer() : enBase(remise);
        quantiteLivree = quantiteLivree.add(remise);
        quantiteBaseLivree = quantiteBaseLivree.add(base);
        return base;
    }

    /** Annulation d'une remise : la quantite redevient a livrer. */
    public void reprendre(BigDecimal remise, BigDecimal base) {
        quantiteLivree = quantiteLivree.subtract(remise);
        quantiteBaseLivree = quantiteBaseLivree.subtract(base);
    }

    /**
     * Le client renonce a une partie du reste : la ligne ne porte plus que ce
     * qu'il garde. Renvoie la quantite qui n'a plus a etre reservee.
     */
    public BigDecimal solder(BigDecimal renonce) {
        BigDecimal base = renonce.compareTo(resteALivrer()) == 0 ? resteBaseALivrer() : enBase(renonce);
        quantite = quantite.subtract(renonce);
        quantiteBase = quantiteBase.subtract(base);
        return base;
    }

    /** Annulation d'un solde : la ligne retrouve ses quantites et ses montants. */
    public void restaurer(BigDecimal renonce, BigDecimal base, BigDecimal remiseRendue, BigDecimal totalRendu) {
        quantite = quantite.add(renonce);
        quantiteBase = quantiteBase.add(base);
        remise = remise.add(remiseRendue);
        total = total.add(totalRendu);
    }

    private BigDecimal enBase(BigDecimal enUniteDeVente) {
        return enUniteDeVente.multiply(quantiteBase).divide(quantite, 3, java.math.RoundingMode.HALF_UP);
    }
}
