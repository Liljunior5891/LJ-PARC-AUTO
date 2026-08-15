package tg.parcauto.api.finance.facture;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;
import tg.parcauto.api.exploitation.client.Client;
import tg.parcauto.api.exploitation.location.Location;
import tg.parcauto.api.exploitation.mission.Mission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Facture client, chapitre 27 du cahier des charges. */
@Entity
@Table(name = "facture")
@Getter
@Setter
@NoArgsConstructor
public class Facture extends BaseEntity {

    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    @Column(nullable = false, unique = true, length = 40)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id")
    private Mission mission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private StatutFacture statut = StatutFacture.BROUILLON;

    @Column(name = "date_emission", nullable = false)
    private LocalDate dateEmission = LocalDate.now();

    @Column(name = "date_echeance")
    private LocalDate dateEcheance;

    @Column(name = "periode_debut")
    private LocalDate periodeDebut;

    @Column(name = "periode_fin")
    private LocalDate periodeFin;

    @Column(name = "montant_ht", nullable = false, precision = 16, scale = 2)
    private BigDecimal montantHt = BigDecimal.ZERO;

    @Column(name = "taux_tva", nullable = false, precision = 5, scale = 2)
    private BigDecimal tauxTva = BigDecimal.valueOf(18);

    @Column(name = "montant_tva", nullable = false, precision = 16, scale = 2)
    private BigDecimal montantTva = BigDecimal.ZERO;

    @Column(name = "montant_ttc", nullable = false, precision = 16, scale = 2)
    private BigDecimal montantTtc = BigDecimal.ZERO;

    @Column(columnDefinition = "text")
    private String observations;

    @OneToMany(mappedBy = "facture", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("ordre asc, id asc")
    private List<FactureLigne> lignes = new ArrayList<>();

    @OneToMany(mappedBy = "facture", fetch = FetchType.LAZY)
    private List<Paiement> paiements = new ArrayList<>();

    public void ajouterLigne(FactureLigne ligne) {
        ligne.setFacture(this);
        ligne.setOrdre(lignes.size());
        lignes.add(ligne);
    }

    /** Recalcule le pied de facture a partir des lignes. */
    public void recalculer() {
        BigDecimal ht = lignes.stream()
                .map(FactureLigne::montantCalcule)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.montantHt = ht.setScale(2, RoundingMode.HALF_UP);
        this.montantTva = ht.multiply(tauxTva).divide(CENT, 2, RoundingMode.HALF_UP);
        this.montantTtc = this.montantHt.add(this.montantTva);
    }

    public BigDecimal montantPaye() {
        return paiements.stream()
                .map(Paiement::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal resteAPayer() {
        return montantTtc.subtract(montantPaye()).max(BigDecimal.ZERO);
    }

    /** Vrai si l'echeance est depassee alors que la facture n'est pas soldee. */
    public boolean enRetard() {
        return statut.attendReglement()
                && dateEcheance != null
                && dateEcheance.isBefore(LocalDate.now())
                && resteAPayer().signum() > 0;
    }

    /**
     * Recalcule le statut de reglement d'apres les paiements enregistres.
     * Une facture en brouillon ou annulee n'est pas concernee.
     */
    public void reevaluerStatut() {
        if (statut == StatutFacture.BROUILLON || statut == StatutFacture.ANNULEE) {
            return;
        }
        BigDecimal paye = montantPaye();
        if (paye.compareTo(montantTtc) >= 0 && montantTtc.signum() > 0) {
            statut = StatutFacture.PAYEE;
        } else if (paye.signum() > 0) {
            statut = enRetard() ? StatutFacture.EN_RETARD : StatutFacture.PARTIELLEMENT_PAYEE;
        } else {
            statut = enRetard() ? StatutFacture.EN_RETARD : StatutFacture.NON_PAYEE;
        }
    }
}
