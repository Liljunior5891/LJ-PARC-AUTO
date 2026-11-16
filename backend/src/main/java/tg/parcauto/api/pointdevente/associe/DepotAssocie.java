package tg.parcauto.api.pointdevente.associe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Versement de sa part a un associe : depot en banque saisi par le gerant, avec
 * le bordereau. L'associe le valide (son sous-compte est alors credite) ou le
 * refuse avec un motif ; le gerant corrige alors et le soumet de nouveau.
 * <p>
 * Tant qu'il n'est pas annule, le depot est engage : l'argent a quitte le coffre
 * ou le compte de l'entreprise et le montant ne reste plus a verser.
 */
@Entity
@Table(name = "pdv_associe_depot")
@Getter
@Setter
@NoArgsConstructor
public class DepotAssocie extends BaseEntity {

    public enum Statut { EN_ATTENTE, VALIDE, REFUSE, ANNULE }

    /** COFFRE : especes du coffre du gerant ; BANQUE : compte de l'entreprise. */
    public enum Source { COFFRE, BANQUE }

    @Column(nullable = false, length = 40)
    private String numero;

    @Column(name = "associe_id", nullable = false)
    private Long associeId;

    @Column(name = "associe_nom", nullable = false, length = 150)
    private String associeNom;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    /** Gerant dont le coffre est debite (source COFFRE). */
    @Column(name = "coffre_id")
    private Long coffreId;

    @Column(length = 100)
    private String banque;

    @Column(nullable = false, length = 80)
    private String reference;

    @Column(name = "date_depot", nullable = false)
    private LocalDate dateDepot;

    /** Periode couverte, en clair : "octobre 2026", "exercice 2026"... */
    @Column(length = 100)
    private String periode;

    @Column(length = 2000)
    private String note;

    @Column(name = "piece_chemin", length = 255)
    private String pieceChemin;

    @Column(name = "piece_nom", length = 255)
    private String pieceNom;

    @Column(name = "piece_type", length = 100)
    private String pieceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.EN_ATTENTE;

    @Column(name = "motif_refus", length = 2000)
    private String motifRefus;

    @Column(name = "auteur_id", nullable = false)
    private Long auteurId;

    @Column(name = "auteur_nom", nullable = false, length = 150)
    private String auteurNom;

    @Column(name = "soumis_le", nullable = false)
    private Instant soumisLe;

    @Column(name = "traite_le")
    private Instant traiteLe;
}
