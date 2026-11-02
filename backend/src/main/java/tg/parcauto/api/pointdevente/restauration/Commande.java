package tg.parcauto.api.pointdevente.restauration;

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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Commande d'une table, ouverte a l'arrivee des clients et close quand toute
 * l'addition est reglee. Les clients commandent en plusieurs fois : chaque
 * envoi en cuisine ou au bar forme un bon numerote.
 */
@Entity
@Table(name = "pdv_commande")
@Getter
@Setter
@NoArgsConstructor
public class Commande extends BaseEntity {

    public enum Statut { OUVERTE, ENCAISSEE, ANNULEE }

    /** Demande faite par le client depuis le code QR de la table. */
    public enum Appel { SERVEUR, ADDITION }

    @Column(nullable = false, length = 40)
    private String numero;

    /** Absente pour une commande au comptoir ou a emporter. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    private TableRestaurant table;

    /** Repere d'une commande sans table (nom du client, a emporter). */
    @Column(length = 80)
    private String libelle;

    /** Nul tant qu'aucun serveur n'a pris en charge une commande ouverte par le client. */
    @Column(name = "serveur_id")
    private Long serveurId;

    /** Session de caisse sur laquelle la commande sera reglee ; sa fermeture attend le reglement. */
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "serveur_nom", nullable = false, length = 150)
    private String serveurNom;

    private Integer couverts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.OUVERTE;

    @Column(name = "ouverte_le", nullable = false)
    private Instant ouverteLe;

    @Column(name = "fermee_le")
    private Instant fermeeLe;

    /** Derniere impression de l'addition : la table attend de payer. */
    @Column(name = "addition_le")
    private Instant additionLe;

    /** Nombre d'envois en preparation deja faits ; le prochain bon porte le numero suivant. */
    @Column(nullable = false)
    private int envois;

    @Column(length = 255)
    private String note;

    /** Appel du client (serveur ou addition), en attente tant qu'un serveur ne l'a pas pris en compte. */
    @Column(name = "appel_le")
    private Instant appelLe;

    @Enumerated(EnumType.STRING)
    @Column(name = "appel_motif", length = 20)
    private Appel appelMotif;

    /** Passee dans la boutique en ligne : signalee comme telle a la caisse et en cuisine. */
    @Column(name = "en_ligne", nullable = false)
    private boolean enLigne;

    /** Commande de la boutique en ligne qu'elle porte. */
    @Column(name = "commande_en_ligne_id")
    private Long commandeEnLigneId;

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<LigneCommande> lignes = new ArrayList<>();

    public void ajouter(LigneCommande ligne) {
        ligne.setCommande(this);
        lignes.add(ligne);
    }

    public boolean ouverte() {
        return statut == Statut.OUVERTE;
    }

    /** Nom affiche : la table, a defaut le libelle ou le numero. */
    public String repere() {
        if (table != null) {
            return table.getNom();
        }
        return libelle != null ? libelle : numero;
    }
}
