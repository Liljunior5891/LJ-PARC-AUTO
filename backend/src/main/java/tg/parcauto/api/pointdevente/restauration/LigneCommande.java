package tg.parcauto.api.pointdevente.restauration;

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
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.PostePreparation;
import tg.parcauto.api.pointdevente.vente.Vente;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Article commande a une table. Il suit le parcours brouillon (saisi, pas
 * encore envoye), a preparer, en preparation, pret, servi ; il peut etre
 * annule. Libelle et prix sont figes a la saisie.
 */
@Entity
@Table(name = "pdv_ligne_commande")
@Getter
@Setter
@NoArgsConstructor
public class LigneCommande extends BaseEntity {

    public enum Statut {
        /** Commandee par le client depuis le code QR, en attente de la validation d'un serveur. */
        PROPOSEE,
        BROUILLON, A_PREPARER, EN_PREPARATION, PRETE, SERVIE, ANNULEE;

        /** Envoyee et non annulee : elle figure sur l'addition. */
        public boolean facturable() {
            return this != PROPOSEE && this != BROUILLON && this != ANNULEE;
        }

        /** Pas encore partie en preparation : elle se modifie ou se retire librement. */
        public boolean enAttente() {
            return this == PROPOSEE || this == BROUILLON;
        }
    }

    public enum Origine { SERVEUR, CLIENT }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commande_id", nullable = false)
    private Commande commande;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    /** Le stock est sorti a l'envoi : seule une ligne sortie se rend a l'annulation. */
    @Column(name = "stock_sorti", nullable = false)
    private boolean stockSorti;

    /** Magasin d'ou le stock est sorti (celui de la caisse) ; nul sans entrepots. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieu_id")
    private tg.parcauto.api.pointdevente.lieu.LieuStock lieu;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(name = "prix_unitaire", nullable = false, precision = 16, scale = 2)
    private BigDecimal prixUnitaire;

    @Column(name = "prix_achat", precision = 16, scale = 2)
    private BigDecimal prixAchat;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantite;

    /** Consigne pour la preparation : cuisson, sans piment... */
    @Column(length = 255)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostePreparation poste;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.BROUILLON;

    private Integer envoi;

    /** Qui a saisi la ligne : un serveur, ou le client depuis le code QR. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Origine origine = Origine.SERVEUR;

    @Column(name = "envoyee_le")
    private Instant envoyeeLe;

    @Column(name = "prete_le")
    private Instant preteLe;

    @Column(name = "servie_le")
    private Instant servieLe;

    @Column(name = "annulee_le")
    private Instant annuleeLe;

    @Column(name = "annulee_par", length = 100)
    private String annuleePar;

    /** Ticket qui a regle la ligne ; nul tant qu'elle reste a payer. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vente_id")
    private Vente vente;

    public BigDecimal total() {
        return prixUnitaire.multiply(quantite);
    }

    public boolean aPayer() {
        return statut.facturable() && vente == null;
    }
}
