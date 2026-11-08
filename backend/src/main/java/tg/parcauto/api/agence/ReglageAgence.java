package tg.parcauto.api.agence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

/** Regles communes a toutes les agences de l'entreprise, reglees par l'administrateur. */
@Entity
@Table(name = "agc_reglage")
@Getter
@Setter
@NoArgsConstructor
public class ReglageAgence extends BaseEntity {

    /** Heure (0 a 23) a laquelle commence la journee de caisse ; avant, l'operation compte pour la veille. */
    @Column(name = "heure_bascule", nullable = false)
    private int heureBascule;

    @Column(name = "delai_doublon_minutes", nullable = false)
    private int delaiDoublonMinutes = 2;

    @Column(name = "montant_max_transaction", nullable = false)
    private long montantMaxTransaction = 2_000_000L;

    @Column(name = "seuil_validation_mouvement", nullable = false)
    private long seuilValidationMouvement = 500_000L;

    @Column(name = "plafond_depense_caissier", nullable = false)
    private long plafondDepenseCaissier = 50_000L;

    /** Jours avant l'echeance d'un credit ou l'alerte est levee. */
    @Column(name = "jours_alerte_echeance", nullable = false)
    private int joursAlerteEcheance = 7;
}
