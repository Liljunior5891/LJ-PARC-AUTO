package tg.parcauto.api.maintenance.incident;

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
import tg.parcauto.api.exploitation.mission.Mission;
import tg.parcauto.api.maintenance.entretien.Entretien;
import tg.parcauto.api.parc.camion.Camion;
import tg.parcauto.api.parc.chauffeur.Chauffeur;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** Incident signale depuis le terrain, chapitre 33 du cahier des charges. */
@Entity
@Table(name = "incident")
@Getter
@Setter
@NoArgsConstructor
public class Incident extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "camion_id", nullable = false)
    private Camion camion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chauffeur_id")
    private Chauffeur chauffeur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id")
    private Mission mission;

    /** Intervention ouverte pour reparer l'incident. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entretien_id")
    private Entretien entretien;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TypeIncident type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GraviteIncident gravite = GraviteIncident.MOYENNE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutIncident statut = StatutIncident.DECLARE;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(length = 255)
    private String lieu;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "survenu_le", nullable = false)
    private Instant survenuLe = Instant.now();

    @Column(name = "resolu_le")
    private Instant resoluLe;

    @Column(columnDefinition = "text")
    private String resolution;

    @Column(name = "cout_estime", precision = 16, scale = 2)
    private BigDecimal coutEstime;

    /** Vrai si le camion est immobilise par l'incident. */
    @Column(nullable = false)
    private boolean immobilisant = false;

    /** Delai ecoule entre la declaration et la resolution, en heures. */
    public Long delaiResolutionHeures() {
        if (resoluLe == null) {
            return null;
        }
        return Duration.between(survenuLe, resoluLe).toHours();
    }

    /** Vrai si l'incident reste ouvert. */
    public boolean ouvert() {
        return !statut.clos();
    }
}
