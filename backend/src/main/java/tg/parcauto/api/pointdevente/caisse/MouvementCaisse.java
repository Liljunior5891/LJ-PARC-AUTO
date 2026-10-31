package tg.parcauto.api.pointdevente.caisse;

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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entree ou sortie d'especes hors vente pendant une session : apport de
 * monnaie, depense payee en liquide, retrait vers le coffre. Elle pese sur les
 * especes attendues a la fermeture.
 */
@Entity
@Table(name = "pdv_mouvement_caisse")
@Getter
@Setter
@NoArgsConstructor
public class MouvementCaisse extends BaseEntity {

    public enum Type { ENTREE, SORTIE }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private SessionCaisse session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false)
    private String motif;

    @Column(name = "auteur_id", nullable = false)
    private Long auteurId;

    @Column(name = "auteur_nom", nullable = false, length = 150)
    private String auteurNom;

    @Column(name = "date_mouvement", nullable = false)
    private Instant dateMouvement;

    /** Montant signe : positif pour une entree, negatif pour une sortie. */
    public BigDecimal effet() {
        return type == Type.ENTREE ? montant : montant.negate();
    }
}
