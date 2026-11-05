package tg.parcauto.api.pointdevente.materiaux;

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
import tg.parcauto.api.pointdevente.caisse.SessionCaisse;
import tg.parcauto.api.pointdevente.vente.ModePaiementPdv;
import tg.parcauto.api.pointdevente.vente.Vente;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Versement d'un client sur une de ses ventes a credit, encaisse sur une
 * session de caisse : il compte dans les especes attendues de cette session.
 * Un versement qui solde plusieurs ventes donne une ligne par vente, sous le
 * meme numero de recu.
 */
@Entity
@Table(name = "pdv_reglement_client")
@Getter
@Setter
@NoArgsConstructor
public class ReglementClient extends BaseEntity {

    public enum Statut { VALIDE, ANNULE }

    @Column(nullable = false, length = 40)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientPdv client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vente_id", nullable = false)
    private Vente vente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private SessionCaisse session;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_paiement", nullable = false, length = 20)
    private ModePaiementPdv modePaiement;

    @Column(name = "reference_paiement", length = 80)
    private String referencePaiement;

    @Column(name = "date_reglement", nullable = false)
    private Instant dateReglement;

    @Column(name = "caissier_nom", nullable = false, length = 150)
    private String caissierNom;

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
