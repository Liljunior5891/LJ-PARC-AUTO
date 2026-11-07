package tg.parcauto.api.pointdevente.tresorerie;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;
import tg.parcauto.api.pointdevente.caisse.MouvementCaisse;
import tg.parcauto.api.pointdevente.caisse.SessionCaisse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Remise de recette d'un caissier en fin de service : son billettage, envoye au
 * gerant qui le recompte et le valide. Validee, la somme recomptee quitte le
 * tiroir pour le coffre du gerant (ou le compte de l'entreprise si c'est
 * l'administrateur qui valide). La session de caisse reste ouverte.
 */
@Entity
@Table(name = "pdv_remise_caisse")
@Getter
@Setter
@NoArgsConstructor
public class RemiseCaisse extends BaseEntity {

    public enum Statut { EN_ATTENTE, VALIDEE, REFUSEE }

    @Column(nullable = false, length = 40)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private SessionCaisse session;

    @Column(name = "caissier_id", nullable = false)
    private Long caissierId;

    @Column(name = "caissier_nom", nullable = false, length = 150)
    private String caissierNom;

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

    @Column(name = "envoyee_le", nullable = false)
    private Instant envoyeeLe;

    @Column(name = "traitee_par_id")
    private Long traiteeParId;

    @Column(name = "traitee_par_nom", length = 150)
    private String traiteeParNom;

    @Column(name = "traitee_le")
    private Instant traiteeLe;

    /** Remarque de celui qui valide (ecart constate) ou motif du refus. */
    @Column(columnDefinition = "text")
    private String commentaire;

    /** Gerant dont le coffre a recu l'argent ; vide quand l'administrateur a valide. */
    @Column(name = "coffre_id")
    private Long coffreId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mouvement_caisse_id")
    private MouvementCaisse mouvementCaisse;

    @ElementCollection
    @CollectionTable(name = "pdv_remise_coupure", joinColumns = @JoinColumn(name = "remise_id"))
    private List<CoupureTresorerie> billettage = new ArrayList<>();
}
