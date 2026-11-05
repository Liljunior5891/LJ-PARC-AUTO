package tg.parcauto.api.pointdevente.vente;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;
import tg.parcauto.api.pointdevente.caisse.SessionCaisse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Ticket de caisse. Une vente validee n'est jamais modifiee : une erreur se
 * corrige par une annulation, qui remet les articles en stock et garde la
 * trace du ticket.
 */
@Entity
@Table(name = "pdv_vente")
@Getter
@Setter
@NoArgsConstructor
public class Vente extends BaseEntity {

    public enum Statut { VALIDEE, ANNULEE }

    @Column(nullable = false, length = 40)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private SessionCaisse session;

    @Column(name = "date_vente", nullable = false)
    private Instant dateVente;

    @Column(name = "caissier_nom", nullable = false, length = 150)
    private String caissierNom;

    @Column(name = "client_nom", length = 150)
    private String clientNom;

    /** Somme des lignes, remises de ligne deduites. */
    @Column(name = "total_brut", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalBrut;

    /** Remise accordee sur l'ensemble du ticket. */
    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal remise = BigDecimal.ZERO;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_paiement", nullable = false, length = 20)
    private ModePaiementPdv modePaiement;

    @Column(name = "montant_recu", precision = 16, scale = 2)
    private BigDecimal montantRecu;

    @Column(name = "monnaie_rendue", precision = 16, scale = 2)
    private BigDecimal monnaieRendue;

    /** Reference de la transaction mobile money ou carte. */
    @Column(name = "reference_paiement", length = 80)
    private String referencePaiement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.VALIDEE;

    @Column(name = "annulee_le")
    private Instant annuleeLe;

    @Column(name = "annulee_par", length = 100)
    private String annuleePar;

    @Column(name = "motif_annulation", length = 255)
    private String motifAnnulation;

    /** Commande de restaurant reglee par ce ticket ; nulle pour une vente au comptoir. */
    @Column(name = "commande_id")
    private Long commandeId;

    /** Encaissee pour une commande passee dans la boutique en ligne. */
    @Column(name = "en_ligne", nullable = false)
    private boolean enLigne;

    /** Table servie, imprimee sur le ticket d'une addition. */
    @Column(name = "table_nom", length = 80)
    private String tableNom;

    /** Compte client (quincaillerie et materiaux) ; nul pour un client de passage. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private tg.parcauto.api.pointdevente.materiaux.ClientPdv client;

    /**
     * Paye a l'encaissement, dans le mode de paiement du ticket : le total pour
     * une vente comptant, l'acompte (eventuellement nul) pour une vente a credit.
     * C'est ce montant qui entre dans la caisse de la session.
     */
    @Column(name = "montant_paye", nullable = false, precision = 16, scale = 2)
    private BigDecimal montantPaye = BigDecimal.ZERO;

    /** Reste du par le client, diminue par ses reglements. */
    @Column(name = "reste_du", nullable = false, precision = 16, scale = 2)
    private BigDecimal resteDu = BigDecimal.ZERO;

    /** Date limite de paiement d'une vente a credit. */
    @Column(name = "echeance")
    private java.time.LocalDate echeance;

    /** Devis accepte dont la vente est issue. */
    @Column(name = "devis_id")
    private Long devisId;

    @OneToMany(mappedBy = "vente", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<LigneVente> lignes = new ArrayList<>();

    public void ajouter(LigneVente ligne) {
        ligne.setVente(this);
        lignes.add(ligne);
    }
}
