package tg.parcauto.api.pointdevente.lieu;

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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Deplacement de marchandise entre deux lieux : demande, validation par le gerant,
 * expedition, reception. Une fois expediee, elle n'est plus dans aucun lieu
 * mais reste dans le stock total de l'entreprise, jusqu'a sa reception.
 */
@Entity
@Table(name = "pdv_transfert")
@Getter
@Setter
@NoArgsConstructor
public class Transfert extends BaseEntity {

    public enum Statut { DEMANDE, VALIDE, EXPEDIE, ECART, RECU, ANNULE }

    @Column(nullable = false, length = 40)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private LieuStock source;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id", nullable = false)
    private LieuStock destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.DEMANDE;

    @Column(length = 255)
    private String note;

    @Column(name = "demande_le", nullable = false)
    private Instant demandeLe;

    @Column(name = "demande_par", nullable = false, length = 150)
    private String demandePar;

    @Column(name = "valide_le")
    private Instant valideLe;

    @Column(name = "valide_par", length = 150)
    private String validePar;

    @Column(name = "expedie_le")
    private Instant expedieLe;

    @Column(name = "expedie_par", length = 150)
    private String expediePar;

    @Column(length = 150)
    private String transporteur;

    @Column(name = "recu_le")
    private Instant recuLe;

    @Column(name = "recu_par", length = 150)
    private String recuPar;

    @Column(name = "annule_le")
    private Instant annuleLe;

    @Column(name = "annule_par", length = 150)
    private String annulePar;

    @Column(length = 255)
    private String motif;

    @OneToMany(mappedBy = "transfert", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneTransfert> lignes = new ArrayList<>();

    public void ajouter(LigneTransfert ligne) {
        ligne.setTransfert(this);
        lignes.add(ligne);
    }
}
