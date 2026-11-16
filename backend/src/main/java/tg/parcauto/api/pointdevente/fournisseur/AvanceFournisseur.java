package tg.parcauto.api.pointdevente.fournisseur;

import jakarta.persistence.Column;
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
import tg.parcauto.api.pointdevente.approvisionnement.CommandeFournisseur;
import tg.parcauto.api.pointdevente.caisse.MouvementCaisse;
import tg.parcauto.api.pointdevente.caisse.SessionCaisse;
import tg.parcauto.api.pointdevente.depense.ModeSortie;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Argent verse a un fournisseur sans marchandise (AVANCE), ou rendu par lui
 * (REMBOURSEMENT, sur le compte de l'entreprise). L'avance regle d'office les
 * dettes nees des receptions suivantes.
 */
@Entity
@Table(name = "pdv_avance_fournisseur")
@Getter
@Setter
@NoArgsConstructor
public class AvanceFournisseur extends BaseEntity {

    public enum Type { AVANCE, REMBOURSEMENT }

    public enum Statut { VALIDE, ANNULE }

    @Column(nullable = false, length = 40)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fournisseur_id", nullable = false)
    private Fournisseur fournisseur;

    /** Commande pour laquelle l'avance est versee, a titre d'information. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id")
    private CommandeFournisseur commande;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModeSortie mode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private SessionCaisse session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mouvement_caisse_id")
    private MouvementCaisse mouvementCaisse;

    @Column(name = "coffre_id")
    private Long coffreId;

    @Column(length = 80)
    private String reference;

    @Column(name = "date_operation", nullable = false)
    private LocalDate dateOperation;

    @Column(length = 255)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.VALIDE;

    @Column(name = "auteur_nom", nullable = false, length = 150)
    private String auteurNom;

    @Column(name = "annule_le")
    private Instant annuleLe;

    @Column(name = "annule_par", length = 150)
    private String annulePar;

    @Column(name = "motif_annulation", length = 255)
    private String motifAnnulation;
}
