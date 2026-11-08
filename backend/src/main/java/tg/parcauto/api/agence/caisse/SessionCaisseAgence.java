package tg.parcauto.api.agence.caisse;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.time.Instant;

/**
 * Session de la caisse d'un caissier : ouverte par un superviseur avec un fond
 * pris sur la caisse generale, fermee par un superviseur apres comptage.
 */
@Entity
@Table(name = "agc_session_caisse")
@Getter
@Setter
@NoArgsConstructor
public class SessionCaisseAgence extends BaseEntity {

    public enum Statut { OUVERTE, FERMEE }

    @Column(name = "agence_id", nullable = false, updatable = false)
    private Long agenceId;

    /** Compte de caisse du caissier (type CAISSE_CAISSIER). */
    @Column(name = "compte_id", nullable = false, updatable = false)
    private Long compteId;

    @Column(name = "caissier_id", nullable = false, updatable = false)
    private Long caissierId;

    @Column(name = "caissier_nom", nullable = false, length = 150, updatable = false)
    private String caissierNom;

    @Column(nullable = false, length = 40, updatable = false)
    private String numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.OUVERTE;

    @Column(name = "ouverte_le", nullable = false, updatable = false)
    private Instant ouverteLe;

    @Column(name = "ouverte_par_id", updatable = false)
    private Long ouverteParId;

    @Column(name = "ouverte_par_nom", length = 150, updatable = false)
    private String ouverteParNom;

    @Column(name = "fond_initial", nullable = false, updatable = false)
    private long fondInitial;

    @Column(name = "billettage_ouverture", columnDefinition = "text", updatable = false)
    private String billettageOuverture;

    @Column(name = "ecriture_ouverture_id")
    private Long ecritureOuvertureId;

    @Column(name = "fermee_le")
    private Instant fermeeLe;

    @Column(name = "fermee_par_id")
    private Long fermeeParId;

    @Column(name = "fermee_par_nom", length = 150)
    private String fermeeParNom;

    @Column(name = "montant_theorique")
    private Long montantTheorique;

    @Column(name = "montant_compte")
    private Long montantCompte;

    private Long ecart;

    @Column(length = 1000)
    private String justification;

    @Column(name = "billettage_fermeture", columnDefinition = "text")
    private String billettageFermeture;

    @Column(name = "ecriture_fermeture_id")
    private Long ecritureFermetureId;
}
