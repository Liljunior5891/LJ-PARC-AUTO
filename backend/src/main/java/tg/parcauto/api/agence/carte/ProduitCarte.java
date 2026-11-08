package tg.parcauto.api.agence.carte;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/** Type de carte prepayee vendue en agence (« Carte Xpress ») et son prix de vente. */
@Entity
@Table(name = "agc_produit_carte")
@Getter
@Setter
@NoArgsConstructor
public class ProduitCarte extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String nom;

    @Column(name = "prix_vente", nullable = false)
    private long prixVente;

    /** Stock a partir duquel une alerte est levee. */
    @Column(name = "seuil_alerte", nullable = false)
    private int seuilAlerte = 2;

    @Column(nullable = false)
    private boolean actif = true;
}
