package tg.parcauto.api.gps.position;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import tg.parcauto.api.parc.camion.Camion;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Position transmise par un traceur, chapitre 22 du cahier des charges.
 * <p>
 * L'entite est volontairement sans audit utilisateur : les positions sont
 * produites par des machines, en volume, et ne sont jamais modifiees.
 */
@Entity
@Table(name = "position_gps")
@Getter
@Setter
@NoArgsConstructor
public class PositionGps {

    /** Rayon moyen de la Terre, pour le calcul des distances. */
    private static final double RAYON_TERRE_METRES = 6_371_000d;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Sans audit, l'entite n'herite pas de BaseEntity : le cloisonnement est pose ici. */
    @org.hibernate.annotations.TenantId
    @Column(name = "entreprise_id", nullable = false, updatable = false)
    private Long entrepriseId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "camion_id", nullable = false)
    private Camion camion;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "vitesse_kmh", precision = 6, scale = 2)
    private BigDecimal vitesseKmh;

    /** Cap en degres, 0 = nord. */
    @Column
    private Integer direction;

    @Column(precision = 8, scale = 2)
    private BigDecimal altitude;

    @Column
    private Long kilometrage;

    @Column(name = "moteur_allume")
    private Boolean moteurAllume;

    @Column(nullable = false)
    private Instant horodatage;

    @Column(length = 60)
    private String fournisseur;

    @Column(name = "device_id", length = 80)
    private String deviceId;

    @CreatedDate
    @Column(name = "cree_le", updatable = false)
    private Instant creeLe;

    /** Vrai si le vehicule est considere en mouvement. */
    public boolean enMouvement() {
        return vitesseKmh != null && vitesseKmh.doubleValue() > 3d;
    }

    /**
     * Distance en metres jusqu'a un point, par la formule de haversine.
     * Precision suffisante pour le geofencing a l'echelle d'un depot ou d'une ville.
     */
    public double distanceMetres(BigDecimal autreLatitude, BigDecimal autreLongitude) {
        double lat1 = Math.toRadians(latitude.doubleValue());
        double lat2 = Math.toRadians(autreLatitude.doubleValue());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(autreLongitude.doubleValue() - longitude.doubleValue());

        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2), 2);
        return 2 * RAYON_TERRE_METRES * Math.asin(Math.min(1d, Math.sqrt(a)));
    }
}
