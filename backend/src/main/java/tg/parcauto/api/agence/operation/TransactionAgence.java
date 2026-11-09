package tg.parcauto.api.agence.operation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.agence.catalogue.TypeOperation;
import tg.parcauto.api.agence.commission.Bareme;
import tg.parcauto.api.common.BaseEntity;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Transaction d'une agence, validee des sa saisie. La regle de mouvement et la
 * commission y sont recopiees ; seule l'annulation change ensuite son statut.
 */
@Entity
@Table(name = "agc_transaction")
@Getter
@Setter
@NoArgsConstructor
public class TransactionAgence extends BaseEntity {

    public enum Statut { VALIDEE, ANNULEE }

    /** ACQUISE : en attente du versement de l'operateur ; CREDITEE : recue sur un compte. */
    public enum StatutCommission { ACQUISE, CREDITEE }

    @Column(name = "agence_id", nullable = false, updatable = false)
    private Long agenceId;

    @Column(nullable = false, length = 30, updatable = false)
    private String reference;

    @Column(name = "date_operation", nullable = false, updatable = false)
    private Instant dateOperation;

    @Column(nullable = false, updatable = false)
    private LocalDate journee;

    @Column(name = "service_id", nullable = false, updatable = false)
    private Long serviceId;

    @Column(name = "reseau_id", updatable = false)
    private Long reseauId;

    @Column(name = "type_operation_id", nullable = false, updatable = false)
    private Long typeOperationId;

    @Column(nullable = false, updatable = false)
    private long montant;

    @Column(name = "effet_cash", nullable = false, updatable = false)
    private int effetCash;

    @Column(name = "effet_virtuel", nullable = false, updatable = false)
    private int effetVirtuel;

    @Enumerated(EnumType.STRING)
    @Column(name = "compte_virtuel", nullable = false, length = 20, updatable = false)
    private TypeOperation.CompteVirtuel compteVirtuel;

    @Column(nullable = false, updatable = false)
    private long commission;

    @Column(name = "commission_proposee", nullable = false, updatable = false)
    private long commissionProposee;

    @Column(name = "bareme_id", updatable = false)
    private Long baremeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "compte_commission", nullable = false, length = 20, updatable = false)
    private Bareme.CompteCredit compteCommission = Bareme.CompteCredit.COMMISSIONS;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_statut", nullable = false, length = 20)
    private StatutCommission commissionStatut = StatutCommission.CREDITEE;

    /** Versement de l'operateur qui a credite la commission. */
    @Column(name = "versement_id")
    private Long versementId;

    @Column(name = "motif_commission", length = 255, updatable = false)
    private String motifCommission;

    @Column(name = "reference_operateur", length = 80, updatable = false)
    private String referenceOperateur;

    @Column(name = "client_id", updatable = false)
    private Long clientId;

    @Column(name = "client_nom", length = 150, updatable = false)
    private String clientNom;

    @Column(name = "client_telephone", length = 30, updatable = false)
    private String clientTelephone;

    @Column(name = "client_type_piece", length = 40, updatable = false)
    private String clientTypePiece;

    @Column(name = "client_numero_piece", length = 60, updatable = false)
    private String clientNumeroPiece;

    @Column(name = "carte_masquee", length = 20, updatable = false)
    private String carteMasquee;

    @Column(length = 500, updatable = false)
    private String observations;

    /** Caisse ou est passe le cash : celle du caissier, ou la caisse generale. */
    @Column(name = "compte_cash_id", updatable = false)
    private Long compteCashId;

    /** Session de caisse du caissier, s'il en avait une ouverte. */
    @Column(name = "session_id", updatable = false)
    private Long sessionId;

    @Column(name = "caissier_id", nullable = false, updatable = false)
    private Long caissierId;

    @Column(name = "caissier_nom", nullable = false, length = 150, updatable = false)
    private String caissierNom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.VALIDEE;

    @Column(name = "doublon_confirme", nullable = false, updatable = false)
    private boolean doublonConfirme;

    @Column(name = "ecriture_id")
    private Long ecritureId;

    @Column(name = "annulation_ecriture_id")
    private Long annulationEcritureId;

    @Column(name = "annulee_le")
    private Instant annuleeLe;

    @Column(name = "annulee_par_id")
    private Long annuleeParId;

    @Column(name = "annulee_par_nom", length = 150)
    private String annuleeParNom;

    @Column(name = "motif_annulation", length = 500)
    private String motifAnnulation;
}
