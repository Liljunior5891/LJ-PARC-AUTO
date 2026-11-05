package tg.parcauto.api.pointdevente.approvisionnement;

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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Commande de reapprovisionnement passee a un fournisseur. Elle reste
 * modifiable tant qu'elle est en cours ; a la reception, les quantites
 * recues entrent en stock et la commande est figee.
 */
@Entity
@Table(name = "pdv_commande_fournisseur")
@Getter
@Setter
@NoArgsConstructor
public class CommandeFournisseur extends BaseEntity {

    public enum Statut { EN_COURS, RECUE, ANNULEE }

    @Column(nullable = false, length = 40)
    private String numero;

    @Column(nullable = false, length = 150)
    private String fournisseur;

    @Column(name = "date_commande", nullable = false)
    private LocalDate dateCommande;

    @Column(name = "livraison_prevue")
    private LocalDate livraisonPrevue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.EN_COURS;

    @Column(length = 500)
    private String note;

    @Column(name = "recue_le")
    private Instant recueLe;

    @Column(name = "recue_par", length = 150)
    private String recuePar;

    /** Lieu qui a recu la marchandise ; nul tant que l'entreprise n'a pas d'entrepot. */
    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "lieu_id")
    private tg.parcauto.api.pointdevente.lieu.LieuStock lieu;

    @Column(name = "annulee_le")
    private Instant annuleeLe;

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<LigneCommandeFournisseur> lignes = new ArrayList<>();

    public void ajouter(LigneCommandeFournisseur ligne) {
        ligne.setCommande(this);
        lignes.add(ligne);
    }

    /** Montant commande, au cout prevu. */
    public BigDecimal total() {
        return lignes.stream().map(LigneCommandeFournisseur::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
