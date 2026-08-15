package tg.parcauto.api.finance.facture;

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
import tg.parcauto.api.finance.commun.ModePaiement;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Reglement d'une facture, chapitre 28 du cahier des charges. */
@Entity
@Table(name = "paiement")
@Getter
@Setter
@NoArgsConstructor
public class Paiement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facture_id", nullable = false)
    private Facture facture;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal montant;

    @Column(name = "date_paiement", nullable = false)
    private LocalDate datePaiement = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "mode_paiement", nullable = false, length = 25)
    private ModePaiement modePaiement = ModePaiement.VIREMENT;

    @Column(length = 80)
    private String reference;

    @Column(columnDefinition = "text")
    private String observations;
}
