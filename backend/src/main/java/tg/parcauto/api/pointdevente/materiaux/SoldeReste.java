package tg.parcauto.api.pointdevente.materiaux;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;
import tg.parcauto.api.pointdevente.caisse.SessionCaisse;
import tg.parcauto.api.pointdevente.vente.ModePaiementPdv;
import tg.parcauto.api.pointdevente.vente.Vente;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Le client renonce a ce qu'il n'a pas encore emporte. La vente est corrigee, sa
 * valeur diminue d'abord ce qu'il doit, et le surplus lui est rembourse sur la
 * session de caisse ouverte : les encaissements d'une session fermee ne bougent pas.
 */
@Entity
@Table(name = "pdv_solde_reste")
@Getter
@Setter
@NoArgsConstructor
public class SoldeReste extends BaseEntity {

    public enum Statut { VALIDE, ANNULE }

    @Column(nullable = false, length = 40)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vente_id", nullable = false)
    private Vente vente;

    /** Session d'ou sort le remboursement ; nulle si rien n'est rembourse. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private SessionCaisse session;

    @Column(name = "date_solde", nullable = false)
    private Instant dateSolde;

    @Column(name = "auteur_nom", nullable = false, length = 150)
    private String auteurNom;

    @Column(nullable = false, length = 255)
    private String motif;

    /** Valeur retiree des lignes, avant la remise globale de la vente. */
    @Column(name = "montant_brut", nullable = false, precision = 16, scale = 2)
    private BigDecimal montantBrut;

    /** Valeur retiree du total de la vente. */
    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal montant;

    /** Part qui a diminue ce que le client doit. */
    @Column(name = "montant_deduit", nullable = false, precision = 16, scale = 2)
    private BigDecimal montantDeduit;

    /** Part rendue au client. */
    @Column(name = "montant_rembourse", nullable = false, precision = 16, scale = 2)
    private BigDecimal montantRembourse;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_remboursement", length = 20)
    private ModePaiementPdv modeRemboursement;

    @Column(name = "reference_paiement", length = 80)
    private String referencePaiement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.VALIDE;

    @Column(name = "annule_le")
    private Instant annuleLe;

    @Column(name = "annule_par", length = 150)
    private String annulePar;

    @Column(name = "motif_annulation", length = 255)
    private String motifAnnulation;

    @OneToMany(mappedBy = "solde", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneSoldeReste> lignes = new ArrayList<>();

    public void ajouter(LigneSoldeReste ligne) {
        ligne.setSolde(this);
        lignes.add(ligne);
    }
}
