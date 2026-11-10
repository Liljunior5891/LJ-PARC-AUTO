package tg.parcauto.api.plateforme.abonnement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.EntiteAuditee;

import java.math.BigDecimal;

/** Reglages des abonnements, une seule ligne (id 1), modifies par le super-administrateur. */
@Entity
@Table(name = "abo_parametres")
@Getter
@Setter
@NoArgsConstructor
public class ParametresAbonnement extends EntiteAuditee {

    @Column(nullable = false, length = 10)
    private String devise = "FCFA";

    @Column(name = "duree_essai_jours", nullable = false)
    private int dureeEssaiJours = 14;

    @Column(name = "jours_grace", nullable = false)
    private int joursGrace = 7;

    @Column(name = "jours_rappel", nullable = false)
    private int joursRappel = 7;

    @Column(name = "remise_trimestrielle", nullable = false)
    private BigDecimal remiseTrimestrielle = BigDecimal.ZERO;

    @Column(name = "remise_semestrielle", nullable = false)
    private BigDecimal remiseSemestrielle = BigDecimal.ZERO;

    @Column(name = "remise_annuelle", nullable = false)
    private BigDecimal remiseAnnuelle = BigDecimal.ZERO;

    @Column(name = "instructions_paiement")
    private String instructionsPaiement;

    @Column(name = "mentions_facture")
    private String mentionsFacture;

    /** Remise en pourcentage pour une periode payee d'avance. */
    public BigDecimal remise(Periodicite p) {
        return switch (p) {
            case MENSUEL -> BigDecimal.ZERO;
            case TRIMESTRIEL -> remiseTrimestrielle;
            case SEMESTRIEL -> remiseSemestrielle;
            case ANNUEL -> remiseAnnuelle;
        };
    }
}
