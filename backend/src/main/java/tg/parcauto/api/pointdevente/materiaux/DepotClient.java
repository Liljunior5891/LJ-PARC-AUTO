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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Argent depose par un client sans achat (DEPOT), ou rendu sur son solde
 * (REMBOURSEMENT). Un depot est valide des sa saisie a la caisse ; un
 * remboursement demande par un caissier attend la validation du gerant.
 */
@Entity
@Table(name = "pdv_depot_client")
@Getter
@Setter
@NoArgsConstructor
public class DepotClient extends BaseEntity {

    public enum Type { DEPOT, REMBOURSEMENT }

    public enum Statut { EN_ATTENTE, VALIDE, REFUSE, ANNULE }

    /** CAISSE : session de caisse ; BANQUE : compte de l'entreprise (remboursement seulement). */
    public enum Source { CAISSE, BANQUE }

    @Column(nullable = false, length = 40)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientPdv client;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_paiement", length = 20)
    private ModePaiementPdv modePaiement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private SessionCaisse session;

    @Column(name = "montant_recu", precision = 16, scale = 2)
    private BigDecimal montantRecu;

    @Column(name = "reference_paiement", length = 80)
    private String referencePaiement;

    @Column(length = 500)
    private String motif;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut;

    @Column(name = "auteur_id", nullable = false)
    private Long auteurId;

    @Column(name = "auteur_nom", nullable = false, length = 150)
    private String auteurNom;

    @Column(name = "date_operation", nullable = false)
    private Instant dateOperation;

    @Column(name = "traite_par", length = 150)
    private String traitePar;

    @Column(name = "traite_le")
    private Instant traiteLe;

    @Column(name = "motif_refus", length = 500)
    private String motifRefus;

    /** Remboursement par la caisse : la sortie d'especes creee a la validation. */
    @Column(name = "mouvement_id")
    private Long mouvementId;
}
