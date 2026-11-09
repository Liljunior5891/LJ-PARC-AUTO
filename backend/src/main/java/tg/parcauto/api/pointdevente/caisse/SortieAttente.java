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
 * Sortie d'especes demandee a la caisse quand la validation des depenses de
 * caisse est activee : elle attend le gerant puis l'administrateur. Tant
 * qu'elle attend, rien ne sort du tiroir ; validee par l'administrateur, elle
 * devient un mouvement de caisse.
 */
@Entity
@Table(name = "pdv_sortie_attente")
@Getter
@Setter
@NoArgsConstructor
public class SortieAttente extends BaseEntity {

    public enum Statut {
        ATTENTE_GERANT, ATTENTE_ADMIN, VALIDEE, REFUSEE;

        public boolean enAttente() {
            return this == ATTENTE_GERANT || this == ATTENTE_ADMIN;
        }
    }

    @Column(nullable = false, length = 40)
    private String numero;

    /** Session d'ou la sortie est demandee. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private SessionCaisse session;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false)
    private String motif;

    @Column(name = "auteur_id", nullable = false)
    private Long auteurId;

    @Column(name = "auteur_nom", nullable = false, length = 150)
    private String auteurNom;

    @Column(name = "demandee_le", nullable = false)
    private Instant demandeeLe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut;

    @Column(name = "visee_par", length = 150)
    private String viseePar;

    @Column(name = "visee_le")
    private Instant viseeLe;

    @Column(name = "approuvee_par", length = 150)
    private String approuveePar;

    @Column(name = "approuvee_le")
    private Instant approuveeLe;

    @Column(name = "refusee_par", length = 150)
    private String refuseePar;

    @Column(name = "refusee_le")
    private Instant refuseeLe;

    @Column(name = "motif_refus", length = 255)
    private String motifRefus;

    /** Mouvement passe a la validation de l'administrateur. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mouvement_caisse_id")
    private MouvementCaisse mouvementCaisse;
}
