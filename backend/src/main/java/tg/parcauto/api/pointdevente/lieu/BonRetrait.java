package tg.parcauto.api.pointdevente.lieu;

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
import tg.parcauto.api.pointdevente.vente.LigneVente;
import tg.parcauto.api.pointdevente.vente.Vente;

import java.time.Instant;
import java.util.List;

/**
 * Bon remis au client a la caisse, a presenter au lieu indique. Son contenu n'est
 * pas copie : il se lit sur les lignes de la vente retirables a ce lieu. Ce qui
 * reste a servir est donc toujours exact, et un bon ne se sert pas deux fois.
 */
@Entity
@Table(name = "pdv_bon_retrait")
@Getter
@Setter
@NoArgsConstructor
public class BonRetrait extends BaseEntity {

    public enum Statut { A_SERVIR, SERVI, ANNULE }

    @Column(nullable = false, length = 40)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vente_id", nullable = false)
    private Vente vente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lieu_id", nullable = false)
    private LieuStock lieu;

    @Column(name = "date_emission", nullable = false)
    private Instant dateEmission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.A_SERVIR;

    /** Lignes de la vente retirables a ce lieu, y compris celles deja servies. */
    public List<LigneVente> lignes() {
        return vente.getLignes().stream()
                .filter(l -> l.getLieu() != null && l.getLieu().getId().equals(lieu.getId()))
                .filter(l -> l.getArticle().isSuiviStock() && l.getQuantite().signum() > 0)
                .toList();
    }
}
