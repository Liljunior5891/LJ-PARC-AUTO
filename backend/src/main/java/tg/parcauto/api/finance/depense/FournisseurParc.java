package tg.parcauto.api.finance.depense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/** Fournisseur de la gestion de parc : garage, station, assureur, pieces... */
@Entity
@Table(name = "parc_fournisseur")
@Getter
@Setter
@NoArgsConstructor
public class FournisseurParc extends BaseEntity {

    @Column(nullable = false, length = 180)
    private String nom;

    @Column(length = 80)
    private String activite;

    @Column(length = 150)
    private String contact;

    @Column(length = 40)
    private String telephone;

    @Column(length = 150)
    private String email;

    @Column(length = 255)
    private String adresse;

    @Column(length = 60)
    private String nif;

    /** Jours accordes pour payer : donnent l'echeance d'une depense a payer. */
    @Column(name = "delai_paiement_jours", nullable = false)
    private int delaiPaiementJours;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private boolean actif = true;
}
