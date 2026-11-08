package tg.parcauto.api.agence.carte;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.time.Instant;

/** Lot de cartes entre en stock dans une agence, a son cout reel d'achat. */
@Entity
@Table(name = "agc_lot_carte")
@Getter
@Setter
@NoArgsConstructor
public class LotCarte extends BaseEntity {

    @Column(name = "agence_id", nullable = false, updatable = false)
    private Long agenceId;

    @Column(name = "produit_id", nullable = false, updatable = false)
    private Long produitId;

    @Column(nullable = false, length = 40, updatable = false)
    private String numero;

    @Column(name = "date_entree", nullable = false, updatable = false)
    private Instant dateEntree;

    @Column(name = "quantite_initiale", nullable = false, updatable = false)
    private int quantiteInitiale;

    @Column(name = "quantite_restante", nullable = false)
    private int quantiteRestante;

    @Column(name = "cout_unitaire", nullable = false, updatable = false)
    private long coutUnitaire;

    @Column(length = 150)
    private String fournisseur;

    @Column(length = 80)
    private String reference;

    /** Compte qui a paye le lot ; nul pour le stock initial. */
    @Column(name = "compte_id", updatable = false)
    private Long compteId;

    @Column(name = "ecriture_id")
    private Long ecritureId;

    @Column(name = "auteur_nom", length = 150, updatable = false)
    private String auteurNom;
}
