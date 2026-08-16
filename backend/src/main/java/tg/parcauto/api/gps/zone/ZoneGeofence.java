package tg.parcauto.api.gps.zone;

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

/** Zone geographique surveillee, chapitre 25 du cahier des charges. */
@Entity
@Table(name = "zone_geofence")
@Getter
@Setter
@NoArgsConstructor
public class ZoneGeofence extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TypeZone type = TypeZone.ZONE_EXPLOITATION;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "rayon_metres", nullable = false)
    private Integer rayonMetres;

    @Column(name = "alerte_entree", nullable = false)
    private boolean alerteEntree = false;

    @Column(name = "alerte_sortie", nullable = false)
    private boolean alerteSortie = false;

    /** Duree de stationnement toleree dans la zone, en minutes. */
    @Column(name = "duree_max_minutes")
    private Integer dureeMaxMinutes;

    @Column(length = 20)
    private String couleur;

    @Column(columnDefinition = "text")
    private String observations;

    @Column(nullable = false)
    private boolean actif = true;
}
