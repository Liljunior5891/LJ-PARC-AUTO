package tg.parcauto.api.pointdevente.fournisseur;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/**
 * Fournisseur du point de vente. Ce qu'on lui doit n'est pas stocke : c'est la
 * valeur des commandes recues moins les reglements valides, toujours juste.
 */
@Entity
@Table(name = "pdv_fournisseur")
@Getter
@Setter
@NoArgsConstructor
public class Fournisseur extends BaseEntity {

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 150)
    private String nom;

    @Column(name = "personne_contact", length = 120)
    private String personneContact;

    @Column(length = 40)
    private String telephone;

    @Column(length = 40)
    private String telephone2;

    @Column(length = 150)
    private String email;

    @Column(length = 255)
    private String adresse;

    @Column(length = 80)
    private String ville;

    @Column(length = 80)
    private String pays;

    @Column(length = 60)
    private String nif;

    @Column(length = 60)
    private String rccm;

    /** Jours accordes pour payer apres la reception ; vide ou 0 : a la reception. */
    @Column(name = "delai_paiement_jours")
    private Integer delaiPaiementJours;

    @Column(columnDefinition = "TEXT")
    private String conditions;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private boolean actif = true;
}
