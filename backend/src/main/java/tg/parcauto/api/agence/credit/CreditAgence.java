package tg.parcauto.api.agence.credit;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Credit, dette, apport ou avance d'une agence. EMPRUNT, APPORT et DETTE sont
 * des dettes de l'agence ; AVANCE est une creance (argent prete ou avance).
 */
@Entity
@Table(name = "agc_credit")
@Getter
@Setter
@NoArgsConstructor
public class CreditAgence extends BaseEntity {

    public enum Type {
        EMPRUNT("Credit recu"), APPORT("Apport de fonds"), AVANCE("Avance accordee"), DETTE("Dette");

        private final String libelle;

        Type(String libelle) {
            this.libelle = libelle;
        }

        public String getLibelle() {
            return libelle;
        }
    }

    public enum Statut { EN_COURS, SOLDE, ANNULE }

    @Column(name = "agence_id", nullable = false, updatable = false)
    private Long agenceId;

    @Column(nullable = false, length = 40, updatable = false)
    private String numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Type type;

    @Column(nullable = false, length = 255)
    private String libelle;

    @Column(name = "tiers_nom", nullable = false, length = 150)
    private String tiersNom;

    @Column(name = "tiers_telephone", length = 30)
    private String tiersTelephone;

    @Column(name = "montant_initial", nullable = false, updatable = false)
    private long montantInitial;

    @Column(name = "capital_restant", nullable = false)
    private long capitalRestant;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_echeance")
    private LocalDate dateEcheance;

    @Column(name = "taux_interet", precision = 7, scale = 4)
    private BigDecimal tauxInteret;

    @Column(name = "compte_id", updatable = false)
    private Long compteId;

    @Column(nullable = false)
    private boolean remboursable = true;

    @Column(length = 500)
    private String observations;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.EN_COURS;

    @Column(name = "ecriture_id")
    private Long ecritureId;

    @Column(name = "auteur_nom", length = 150, updatable = false)
    private String auteurNom;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "agc_credit_echeance", joinColumns = @JoinColumn(name = "credit_id"))
    @OrderBy("datePrevue")
    private List<EcheanceCredit> echeances = new ArrayList<>();

    /** Dette de l'agence (elle rembourse) ; faux pour une avance qu'elle a accordee. */
    public boolean estDette() {
        return type != Type.AVANCE;
    }
}
