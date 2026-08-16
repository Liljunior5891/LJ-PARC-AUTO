package tg.parcauto.api.gps.alerte;

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
import tg.parcauto.api.gps.zone.ZoneGeofence;
import tg.parcauto.api.parc.camion.Camion;

import java.math.BigDecimal;
import java.time.Instant;

/** Alerte de geolocalisation, chapitre 26 du cahier des charges. */
@Entity
@Table(name = "alerte_gps")
@Getter
@Setter
@NoArgsConstructor
public class AlerteGps extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "camion_id", nullable = false)
    private Camion camion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private ZoneGeofence zone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TypeAlerteGps type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GraviteAlerte gravite = GraviteAlerte.AVERTISSEMENT;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "vitesse_kmh", precision = 6, scale = 2)
    private BigDecimal vitesseKmh;

    @Column(nullable = false)
    private Instant horodatage = Instant.now();

    @Column(nullable = false)
    private boolean traitee = false;

    @Column(name = "traitee_par", length = 100)
    private String traiteePar;

    @Column(name = "traitee_le")
    private Instant traiteeLe;
}
