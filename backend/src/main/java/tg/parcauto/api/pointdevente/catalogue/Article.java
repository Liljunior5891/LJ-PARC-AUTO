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
 * Article vendu en caisse.
 * <p>
 * Le stock courant est porte par l'article et tenu a jour par chaque
 * mouvement ; le verrouillage optimiste herite de {@link BaseEntity} empeche
 * deux ventes simultanees d'ecraser mutuellement leur decrement.
 */
@Entity
@Table(name = "pdv_article")
@Getter
@Setter
@NoArgsConstructor
public class Article extends BaseEntity {

    @Column(nullable = false, length = 40)
    private String code;

    /** EAN ou code interne lu par la douchette ; facultatif. */
    @Column(name = "code_barres", length = 60)
    private String codeBarres;

    @Column(nullable = false, length = 150)
    private String nom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id")
    private Categorie categorie;

    @Column(name = "prix_vente", nullable = false, precision = 16, scale = 2)
    private BigDecimal prixVente;

    /** Cout d'achat, pour la marge ; facultatif. */
    @Column(name = "prix_achat", precision = 16, scale = 2)
    private BigDecimal prixAchat;

    /** Unite de vente affichee : piece, kg, litre... */
    @Column(length = 20)
    private String unite;

    /** Faux pour un service ou un article non stocke : la vente ne touche pas au stock. */
    @Column(name = "suivi_stock", nullable = false)
    private boolean suiviStock = true;

    @Column(name = "stock_actuel", nullable = false, precision = 14, scale = 3)
    private BigDecimal stockActuel = BigDecimal.ZERO;

    @Column(name = "seuil_alerte", precision = 14, scale = 3)
    private BigDecimal seuilAlerte;

    @Column(nullable = false)
    private boolean actif = true;

    /** Photo reduite par le navigateur, en data URL (image/jpeg, image/png ou image/webp). */
    @Column(columnDefinition = "text")
    private String image;

    /** Vrai si le stock suivi est passe sous le seuil d'alerte. */
    public boolean stockBas() {
        return suiviStock && seuilAlerte != null && stockActuel.compareTo(seuilAlerte) <= 0;
    }
}
