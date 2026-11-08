package tg.parcauto.api.agence.tresorerie;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** Variation d'un compte : montant signe et solde du compte juste apres. */
@Entity
@Table(name = "agc_ligne_registre")
@Getter
@Setter
@NoArgsConstructor
public class LigneRegistre extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ecriture_id", nullable = false, updatable = false)
    private Ecriture ecriture;

    @Column(name = "compte_id", nullable = false, updatable = false)
    private Long compteId;

    @Column(name = "agence_id", nullable = false, updatable = false)
    private Long agenceId;

    @Column(name = "service_id", updatable = false)
    private Long serviceId;

    @Column(name = "reseau_id", updatable = false)
    private Long reseauId;

    /** Positif, le compte augmente ; negatif, il diminue. */
    @Column(nullable = false, updatable = false)
    private long montant;

    @Column(name = "solde_apres", nullable = false, updatable = false)
    private long soldeApres;

    @Column(name = "date_operation", nullable = false, updatable = false)
    private Instant dateOperation;

    @Column(nullable = false, updatable = false)
    private LocalDate journee;
}
