package tg.parcauto.api.parc.document;

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
import java.time.temporal.ChronoUnit;

/** Document administratif rattache a un camion ou a un chauffeur. */
@Entity
@Table(name = "document")
@Getter
@Setter
@NoArgsConstructor
public class Document extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "proprietaire_type", nullable = false, length = 20)
    private ProprietaireDocument proprietaireType;

    @Column(name = "proprietaire_id", nullable = false)
    private Long proprietaireId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TypeDocument type;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(length = 80)
    private String numero;

    @Column(name = "emetteur", length = 150)
    private String emetteur;

    @Column(name = "date_emission")
    private LocalDate dateEmission;

    @Column(name = "date_expiration")
    private LocalDate dateExpiration;

    @Column(precision = 16, scale = 2)
    private BigDecimal cout;

    @Column(name = "fichier_nom", length = 255)
    private String fichierNom;

    @Column(name = "fichier_chemin", length = 500)
    private String fichierChemin;

    @Column(name = "fichier_type", length = 120)
    private String fichierType;

    @Column(name = "fichier_taille")
    private Long fichierTaille;

    @Column(columnDefinition = "text")
    private String observations;

    @Column(nullable = false)
    private boolean archive = false;

    public boolean expire() {
        return dateExpiration != null && dateExpiration.isBefore(LocalDate.now());
    }

    /** Nombre de jours avant expiration ; negatif si le document est deja expire. */
    public Long joursAvantExpiration() {
        return dateExpiration == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), dateExpiration);
    }
}
