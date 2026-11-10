package tg.parcauto.api.marketplace;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Commande passee en ligne par un client sans compte, suivie par un lien a jeton. */
@Entity
@Table(name = "mkt_commande")
@Getter
@Setter
@NoArgsConstructor
public class CommandeEnLigne extends BaseEntity {

    public enum ModeRemise {
        RETRAIT("Retrait en boutique"),
        LIVRAISON("Livraison");

        private final String libelle;

        ModeRemise(String libelle) {
            this.libelle = libelle;
        }

        public String getLibelle() {
            return libelle;
        }
    }

    public enum ModePaiement {
        A_LA_REMISE("Paiement au retrait / a la livraison"),
        MOBILE_MONEY("Mobile money en ligne");

        private final String libelle;

        ModePaiement(String libelle) {
            this.libelle = libelle;
        }

        public String getLibelle() {
            return libelle;
        }
    }

    public enum StatutPaiement {
        EN_ATTENTE, PAYEE, ECHOUEE, REMBOURSEE
    }

    /** Cycle de vie : la commande avance dans cet ordre, ou s'arrete annulee. */
    public enum Statut {
        NOUVELLE("Nouvelle"),
        CONFIRMEE("Confirmee"),
        PRETE("Prete"),
        EN_LIVRAISON("En livraison"),
        LIVREE("Livree / retiree"),
        ANNULEE("Annulee");

        private final String libelle;

        Statut(String libelle) {
            this.libelle = libelle;
        }

        public String getLibelle() {
            return libelle;
        }

        public boolean terminal() {
            return this == LIVREE || this == ANNULEE;
        }
    }

    @Column(nullable = false, length = 40)
    private String numero;

    @Column(nullable = false, length = 32)
    private String jeton;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "client_nom", nullable = false, length = 150)
    private String clientNom;

    /** Nom de famille saisi dans la boutique (vide pour les commandes anterieures a V32). */
    @Column(name = "client_nom_famille", length = 100)
    private String clientNomFamille;

    @Column(name = "client_prenoms", length = 100)
    private String clientPrenoms;

    @Column(name = "client_telephone", nullable = false, length = 40)
    private String clientTelephone;

    @Column(name = "client_email", length = 150)
    private String clientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_remise", nullable = false, length = 20)
    private ModeRemise modeRemise;

    @Column(name = "adresse_livraison", length = 255)
    private String adresseLivraison;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_paiement", nullable = false, length = 20)
    private ModePaiement modePaiement;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_paiement", nullable = false, length = 20)
    private StatutPaiement statutPaiement = StatutPaiement.EN_ATTENTE;

    @Column(name = "reference_paiement", length = 100)
    private String referencePaiement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.NOUVELLE;

    @Column(name = "sous_total", nullable = false, precision = 16, scale = 2)
    private BigDecimal sousTotal;

    @Column(name = "frais_livraison", nullable = false, precision = 16, scale = 2)
    private BigDecimal fraisLivraison = BigDecimal.ZERO;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal total;

    @Column(length = 500)
    private String note;

    @Column(name = "motif_annulation", length = 255)
    private String motifAnnulation;

    @Column(name = "date_commande", nullable = false)
    private Instant dateCommande;

    /**
     * Confiee a la caisse du point de vente : acceptee, preparee et encaissee
     * comme une commande sur place. Le stock sort alors avec le point de vente
     * (envoi en cuisine ou encaissement), pas a la commande.
     */
    @Column(name = "en_caisse", nullable = false)
    private boolean enCaisse;

    /** Restaurant : commande de salle qui la porte jusqu'aux ecrans de preparation. */
    @Column(name = "pdv_commande_id")
    private Long pdvCommandeId;

    /** Ticket de la caisse boutique qui a regle la commande. */
    @Column(name = "vente_id")
    private Long venteId;

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<LigneCommandeEnLigne> lignes = new ArrayList<>();

    public void ajouter(LigneCommandeEnLigne ligne) {
        ligne.setCommande(this);
        lignes.add(ligne);
    }
}
