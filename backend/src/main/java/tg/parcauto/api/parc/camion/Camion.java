package tg.parcauto.api.parc.camion;

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
import java.time.LocalDate;

/** Fiche camion, chapitre 6.1 du cahier des charges. */
@Entity
@Table(name = "camion")
@Getter
@Setter
@NoArgsConstructor
public class Camion extends BaseEntity {

    @Column(name = "numero_interne", nullable = false, unique = true, length = 40)
    private String numeroInterne;

    @Column(nullable = false, unique = true, length = 40)
    private String immatriculation;

    @Column(length = 60)
    private String marque;

    @Column(length = 60)
    private String modele;

    @Column(name = "annee")
    private Integer annee;

    /** Type de vehicule : benne, plateau, citerne, porte-conteneur... */
    @Column(length = 60)
    private String type;

    @Column(length = 60)
    private String categorie;

    @Column(name = "numero_chassis", length = 60)
    private String numeroChassis;

    @Column(name = "numero_moteur", length = 60)
    private String numeroMoteur;

    @Column(length = 40)
    private String couleur;

    /** Capacite utile en tonnes. */
    @Column(name = "capacite_tonnes", precision = 10, scale = 2)
    private BigDecimal capaciteTonnes;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_carburant", length = 20)
    private TypeCarburant typeCarburant = TypeCarburant.DIESEL;

    @Column(name = "date_acquisition")
    private LocalDate dateAcquisition;

    @Column(name = "prix_achat", precision = 16, scale = 2)
    private BigDecimal prixAchat;

    @Column(name = "kilometrage_initial", nullable = false)
    private Long kilometrageInitial = 0L;

    @Column(name = "kilometrage_actuel", nullable = false)
    private Long kilometrageActuel = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatutCamion statut = StatutCamion.DISPONIBLE;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "gps_device_id", length = 80)
    private String gpsDeviceId;

    /** Seuil de vitesse propre a ce camion, exploite par les alertes GPS (chapitre 26). */
    @Column(name = "vitesse_max_kmh")
    private Integer vitesseMaxKmh;

    @Column(columnDefinition = "text")
    private String observations;

    @Column(nullable = false)
    private boolean actif = true;

    /** Kilometres parcourus depuis la mise en service. */
    public long kilometrageParcouru() {
        return Math.max(0L, kilometrageActuel - kilometrageInitial);
    }
}
