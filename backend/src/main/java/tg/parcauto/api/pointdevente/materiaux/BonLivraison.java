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
import tg.parcauto.api.pointdevente.vente.Vente;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Bon de livraison : ce qui a ete remis au client lors d'un passage. Une vente
 * peut en avoir plusieurs, jusqu'a ce que tout soit remis.
 */
@Entity
@Table(name = "pdv_bon_livraison")
@Getter
@Setter
@NoArgsConstructor
public class BonLivraison extends BaseEntity {

    public enum Statut { VALIDE, ANNULE }

    @Column(nullable = false, length = 40)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vente_id", nullable = false)
    private Vente vente;

    @Column(name = "date_bon", nullable = false)
    private Instant dateBon;

    @Column(name = "remis_par", nullable = false, length = 150)
    private String remisPar;

    /** Personne qui a pris la marchandise : le client, son chauffeur, son chef de chantier. */
    @Column(name = "recu_par", length = 150)
    private String recuPar;

    @Column(length = 255)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.VALIDE;

    @Column(name = "annule_le")
    private Instant annuleLe;

    @Column(name = "annule_par", length = 150)
    private String annulePar;

    @Column(name = "motif_annulation", length = 255)
    private String motifAnnulation;

    /** Lieu d'ou la marchandise est sortie ; nul tant que l'entreprise n'a pas d'entrepot. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieu_id")
    private tg.parcauto.api.pointdevente.lieu.LieuStock lieu;

    @OneToMany(mappedBy = "bon", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneBonLivraison> lignes = new ArrayList<>();

    public void ajouter(LigneBonLivraison ligne) {
        ligne.setBon(this);
        lignes.add(ligne);
    }
}
