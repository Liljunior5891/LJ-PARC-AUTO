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
 * Paiement d'un fournisseur sur une de ses commandes recues. Un paiement qui
 * solde plusieurs commandes donne une ligne par commande, sous le meme numero.
 */
@Entity
@Table(name = "pdv_reglement_fournisseur")
@Getter
@Setter
@NoArgsConstructor
public class ReglementFournisseur extends BaseEntity {

    public enum Statut { VALIDE, ANNULE }

    @Column(nullable = false, length = 40)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fournisseur_id", nullable = false)
    private Fournisseur fournisseur;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commande_id", nullable = false)
    private CommandeFournisseur commande;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModeSortie mode;

    @Column(length = 80)
    private String reference;

    @Column(name = "date_reglement", nullable = false)
    private LocalDate dateReglement;

    /** Session de caisse d'ou sont sorties les especes (mode CAISSE). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private SessionCaisse session;

    /** Sortie d'especes creee dans la session ; la meme pour toutes les lignes du paiement. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mouvement_caisse_id")
    private MouvementCaisse mouvementCaisse;

    @Column(name = "auteur_nom", nullable = false, length = 150)
    private String auteurNom;

    @Column(length = 255)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.VALIDE;

    @Column(name = "annule_le")
    private Instant annuleLe;

    @Column(name = "annule_par", length = 150)
    private String annulePar;

    @Column(name = "motif_annulation", length = 255)
    private String motifAnnulation;
}
