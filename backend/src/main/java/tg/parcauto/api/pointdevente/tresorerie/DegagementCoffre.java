package tg.parcauto.api.pointdevente.tresorerie;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Degagement du coffre d'un gerant vers l'administrateur : billettage du
 * gerant, recompte et validation par l'administrateur. Valide, la somme
 * recomptee quitte le coffre et arrive sur le compte de l'entreprise.
 */
@Entity
@Table(name = "pdv_degagement")
@Getter
@Setter
@NoArgsConstructor
public class DegagementCoffre extends BaseEntity {

    public enum Statut { EN_ATTENTE, VALIDE, REFUSE }

    @Column(nullable = false, length = 40)
    private String numero;

    @Column(name = "gerant_id", nullable = false)
    private Long gerantId;

    @Column(name = "gerant_nom", nullable = false, length = 150)
    private String gerantNom;

    @Column(name = "montant_attendu", nullable = false, precision = 16, scale = 2)
    private BigDecimal montantAttendu;

    @Column(name = "montant_declare", nullable = false, precision = 16, scale = 2)
    private BigDecimal montantDeclare;

    @Column(name = "montant_valide", precision = 16, scale = 2)
    private BigDecimal montantValide;

    @Column(columnDefinition = "text")
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.EN_ATTENTE;

    @Column(name = "envoye_le", nullable = false)
    private Instant envoyeLe;

    @Column(name = "traite_par_id")
    private Long traiteParId;

    @Column(name = "traite_par_nom", length = 150)
    private String traiteParNom;

    @Column(name = "traite_le")
    private Instant traiteLe;

    @Column(columnDefinition = "text")
    private String commentaire;

    /** Depot en banque : banque, reference et date du bordereau. */
    @Column(length = 100)
    private String banque;

    @Column(name = "reference_depot", length = 80)
    private String referenceDepot;

    @Column(name = "date_depot")
    private java.time.LocalDate dateDepot;

    /** Bordereau de versement ou releve de banque, exige avant la validation. */
    @Column(name = "piece_chemin", length = 255)
    private String pieceChemin;

    @Column(name = "piece_nom", length = 255)
    private String pieceNom;

    @Column(name = "piece_type", length = 100)
    private String pieceType;

    @ElementCollection
    @CollectionTable(name = "pdv_degagement_coupure", joinColumns = @JoinColumn(name = "degagement_id"))
    private List<CoupureTresorerie> billettage = new ArrayList<>();
}
