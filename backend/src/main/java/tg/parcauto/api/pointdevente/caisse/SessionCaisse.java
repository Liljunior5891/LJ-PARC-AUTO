package tg.parcauto.api.pointdevente.caisse;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Periode d'activite d'une caisse, ouverte par un gerant avec un fond de caisse
 * compte par coupure, puis fermee par un gerant apres comptage du tiroir. Tout
 * le personnel affecte a la caisse encaisse sur la session. L'ecart avec le
 * montant attendu revele les erreurs de rendu de monnaie.
 */
@Entity
@Table(name = "pdv_session_caisse")
@Getter
@Setter
@NoArgsConstructor
public class SessionCaisse extends BaseEntity {

    public enum Statut { OUVERTE, FERMEE }

    @Column(nullable = false, length = 40)
    private String numero;

    /** Caisse tenue pendant la session. */
    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY, optional = false)
    @jakarta.persistence.JoinColumn(name = "caisse_id", nullable = false)
    private Caisse caisse;

    /** Gerant qui a ouvert la session. */
    @Column(name = "caissier_id", nullable = false)
    private Long caissierId;

    @Column(name = "caissier_nom", nullable = false, length = 150)
    private String caissierNom;

    /** Gerant qui a ferme la session. */
    @Column(name = "fermee_par_id")
    private Long fermeeParId;

    @Column(name = "fermee_par_nom", length = 150)
    private String fermeeParNom;

    /** Explication obligatoire d'un ecart non nul a la fermeture. */
    @Column(name = "justification_ecart", columnDefinition = "text")
    private String justificationEcart;

    /** Billets et pieces comptes a l'ouverture et a la fermeture. */
    @jakarta.persistence.ElementCollection
    @jakarta.persistence.CollectionTable(name = "pdv_billettage",
            joinColumns = @jakarta.persistence.JoinColumn(name = "session_id"))
    private java.util.List<Coupure> billettage = new java.util.ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.OUVERTE;

    @Column(name = "ouverte_le", nullable = false)
    private Instant ouverteLe;

    @Column(name = "fond_initial", nullable = false, precision = 16, scale = 2)
    private BigDecimal fondInitial = BigDecimal.ZERO;

    @Column(name = "fermee_le")
    private Instant fermeeLe;

    /** Especes comptees par le caissier a la cloture. */
    @Column(name = "montant_compte", precision = 16, scale = 2)
    private BigDecimal montantCompte;

    /** Fond initial augmente des encaissements en especes. */
    @Column(name = "montant_theorique", precision = 16, scale = 2)
    private BigDecimal montantTheorique;

    /** Compte moins theorique : negatif s'il manque de l'argent. */
    @Column(precision = 16, scale = 2)
    private BigDecimal ecart;

    @Column(columnDefinition = "text")
    private String observations;

    public boolean ouverte() {
        return statut == Statut.OUVERTE;
    }

    public java.util.List<Coupure> billettage(Coupure.Moment moment) {
        return billettage.stream().filter(c -> c.getMoment() == moment).toList();
    }
}
