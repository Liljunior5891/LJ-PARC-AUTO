package tg.parcauto.api.agence.carte;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Vente de cartes prepayees : prix encaisse, cout reel des lots consommes et marge brute. */
@Entity
@Table(name = "agc_vente_carte")
@Getter
@Setter
@NoArgsConstructor
public class VenteCarte extends BaseEntity {

    public enum Statut { VALIDEE, ANNULEE }

    @Column(name = "agence_id", nullable = false, updatable = false)
    private Long agenceId;

    @Column(name = "produit_id", nullable = false, updatable = false)
    private Long produitId;

    @Column(nullable = false, length = 40, updatable = false)
    private String reference;

    @Column(name = "date_vente", nullable = false, updatable = false)
    private Instant dateVente;

    @Column(nullable = false, updatable = false)
    private LocalDate journee;

    @Column(nullable = false, updatable = false)
    private int quantite;

    @Column(name = "prix_unitaire", nullable = false, updatable = false)
    private long prixUnitaire;

    @Column(nullable = false, updatable = false)
    private long montant;

    @Column(name = "cout_total", nullable = false, updatable = false)
    private long coutTotal;

    @Column(nullable = false, updatable = false)
    private long marge;

    @Column(name = "compte_cash_id", nullable = false, updatable = false)
    private Long compteCashId;

    @Column(name = "session_id", updatable = false)
    private Long sessionId;

    @Column(name = "client_nom", length = 150, updatable = false)
    private String clientNom;

    @Column(name = "client_prenom", length = 150, updatable = false)
    private String clientPrenom;

    @Column(name = "client_telephone", length = 30, updatable = false)
    private String clientTelephone;

    @Column(name = "caissier_id", nullable = false, updatable = false)
    private Long caissierId;

    @Column(name = "caissier_nom", nullable = false, length = 150, updatable = false)
    private String caissierNom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.VALIDEE;

    @Column(name = "ecriture_id")
    private Long ecritureId;

    @Column(name = "annulation_ecriture_id")
    private Long annulationEcritureId;

    @Column(name = "annulee_le")
    private Instant annuleeLe;

    @Column(name = "annulee_par_nom", length = 150)
    private String annuleeParNom;

    @Column(name = "motif_annulation", length = 500)
    private String motifAnnulation;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "agc_vente_carte_lot", joinColumns = @JoinColumn(name = "vente_id"))
    private List<ConsommationLot> lots = new ArrayList<>();

    /** Numero de chaque carte vendue, dans l'ordre de saisie. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "agc_vente_carte_numero", joinColumns = @JoinColumn(name = "vente_id"))
    @Column(name = "numero", length = 40)
    private List<String> numeros = new ArrayList<>();
}
