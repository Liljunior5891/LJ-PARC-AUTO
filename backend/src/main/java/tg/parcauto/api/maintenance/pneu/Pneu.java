package tg.parcauto.api.maintenance.pneu;

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
import tg.parcauto.api.finance.depense.Depense;
import tg.parcauto.api.parc.camion.Camion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Pneumatique suivi individuellement, chapitre 20 du cahier des charges. */
@Entity
@Table(name = "pneu")
@Getter
@Setter
@NoArgsConstructor
public class Pneu extends BaseEntity {

    /** Part de la duree de vie au-dela de laquelle le pneu est signale. */
    private static final double SEUIL_USURE = 0.85d;

    /** Camion porteur ; nul tant que le pneu est en stock. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camion_id")
    private Camion camion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depense_id")
    private Depense depense;

    @Column(nullable = false, unique = true, length = 60)
    private String reference;

    @Column(length = 80)
    private String marque;

    @Column(length = 40)
    private String dimension;

    @Column(name = "numero_serie", length = 80)
    private String numeroSerie;

    /** Position sur le vehicule : AVG, AVD, ARG1, ARD2, SECOURS... */
    @Column(length = 30)
    private String position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutPneu statut = StatutPneu.EN_STOCK;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EtatPneu etat = EtatPneu.NEUF;

    @Column(name = "date_achat")
    private LocalDate dateAchat;

    @Column(name = "prix_achat", precision = 16, scale = 2)
    private BigDecimal prixAchat;

    @Column(name = "date_montage")
    private LocalDate dateMontage;

    @Column(name = "kilometrage_montage")
    private Long kilometrageMontage;

    @Column(name = "date_depose")
    private LocalDate dateDepose;

    @Column(name = "kilometrage_depose")
    private Long kilometrageDepose;

    @Column(name = "duree_vie_prevue_km")
    private Long dureeViePrevueKm;

    @Column(name = "motif_depose", length = 255)
    private String motifDepose;

    @Column(columnDefinition = "text")
    private String observations;

    /**
     * Kilometres parcourus par le pneu : depuis le montage jusqu'a la depose,
     * ou jusqu'au compteur actuel du camion s'il est toujours monte.
     */
    public Long kilometresParcourus() {
        if (kilometrageMontage == null) {
            return null;
        }
        Long fin = kilometrageDepose != null ? kilometrageDepose
                : camion != null ? camion.getKilometrageActuel() : null;
        return fin == null ? null : Math.max(0L, fin - kilometrageMontage);
    }

    /** Part de la duree de vie consommee, en pourcentage. */
    public Integer usurePourcent() {
        Long parcourus = kilometresParcourus();
        if (parcourus == null || dureeViePrevueKm == null || dureeViePrevueKm == 0) {
            return null;
        }
        return (int) Math.min(100, Math.round(parcourus * 100d / dureeViePrevueKm));
    }

    /** Vrai si le pneu approche de sa fin de vie ou est declare critique. */
    public boolean aRemplacer() {
        if (statut != StatutPneu.MONTE) {
            return false;
        }
        if (etat.critique()) {
            return true;
        }
        Long parcourus = kilometresParcourus();
        return parcourus != null && dureeViePrevueKm != null
                && parcourus >= dureeViePrevueKm * SEUIL_USURE;
    }

    /** Cout au kilometre du pneu, indicateur de rentabilite d'une marque. */
    public BigDecimal coutParKm() {
        Long parcourus = kilometresParcourus();
        if (prixAchat == null || parcourus == null || parcourus == 0) {
            return null;
        }
        return prixAchat.divide(BigDecimal.valueOf(parcourus), 2, RoundingMode.HALF_UP);
    }
}
