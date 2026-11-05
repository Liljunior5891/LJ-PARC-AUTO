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
 * Devis (facture pro forma) remis au client avant l'achat. Ses prix sont
 * garantis jusqu'a sa date de validite ; accepte, il devient une vente a ces
 * prix, en une fois. Il ne reserve pas le stock.
 */
@Entity
@Table(name = "pdv_devis")
@Getter
@Setter
@NoArgsConstructor
public class Devis extends BaseEntity {

    public enum Statut { EN_COURS, CONVERTI, ANNULE }

    @Column(nullable = false, length = 40)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private ClientPdv client;

    /** Client sans fiche ; pour un client a fiche, son nom au moment du devis. */
    @Column(name = "client_nom", length = 150)
    private String clientNom;

    @Column(name = "date_devis", nullable = false)
    private LocalDate dateDevis;

    @Column(name = "valable_jusqu", nullable = false)
    private LocalDate valableJusqu;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.EN_COURS;

    @Column(name = "total_brut", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalBrut;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal remise = BigDecimal.ZERO;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal total;

    @Column(length = 500)
    private String note;

    @Column(name = "vendeur_nom", nullable = false, length = 150)
    private String vendeurNom;

    /** Vente nee du devis accepte. */
    @Column(name = "vente_id")
    private Long venteId;

    @Column(name = "annule_le")
    private Instant annuleLe;

    @OneToMany(mappedBy = "devis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<LigneDevis> lignes = new ArrayList<>();

    public void ajouter(LigneDevis ligne) {
        ligne.setDevis(this);
        lignes.add(ligne);
    }

    /** Encore modifiable et encaissable, mais sa date de validite est passee. */
    public boolean expire() {
        return statut == Statut.EN_COURS && valableJusqu.isBefore(LocalDate.now());
    }
}
