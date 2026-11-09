package tg.parcauto.api.presence;

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

import java.time.Instant;
import java.time.LocalDate;

/** Arrivee d'un employe : une par jour. */
@Entity
@Table(name = "pres_pointage")
@Getter
@Setter
@NoArgsConstructor
public class PointagePresence extends BaseEntity {

    public enum Mode { TELEPHONE, BORNE, MANUEL }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employe_id")
    private EmployePresence employe;

    @Column(nullable = false)
    private LocalDate jour;

    @Column(name = "arrivee_le", nullable = false)
    private Instant arriveeLe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lieu_id")
    private LieuPresence lieu;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Mode mode;

    @Column(name = "distance_metres")
    private Integer distanceMetres;

    @Column(name = "precision_metres")
    private Integer precisionMetres;

    @Column(name = "retard_minutes", nullable = false)
    private int retardMinutes;

    /** Arrive apres l'heure limite : accepte, compte comme un gros retard. */
    @Column(name = "apres_limite", nullable = false)
    private boolean apresLimite;

    @Column(name = "saisi_par", length = 150)
    private String saisiPar;

    @Column(length = 255)
    private String motif;
}
