package tg.parcauto.api.agence.depense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/** Charge qui revient chaque mois dans une agence (loyer, internet...), reglee une seule fois par mois. */
@Entity
@Table(name = "agc_charge_recurrente")
@Getter
@Setter
@NoArgsConstructor
public class ChargeRecurrente extends BaseEntity {

    @Column(name = "agence_id", nullable = false, updatable = false)
    private Long agenceId;

    @Column(name = "categorie_id", nullable = false)
    private Long categorieId;

    @Column(nullable = false, length = 120)
    private String libelle;

    @Column(nullable = false)
    private long montant;

    @Column(name = "jour_echeance", nullable = false)
    private int jourEcheance = 1;

    @Column(nullable = false)
    private boolean actif = true;
}
