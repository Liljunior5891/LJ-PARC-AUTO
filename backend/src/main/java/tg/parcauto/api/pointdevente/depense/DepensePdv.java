package tg.parcauto.api.pointdevente.depense;

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
import tg.parcauto.api.pointdevente.caisse.MouvementCaisse;
import tg.parcauto.api.pointdevente.caisse.SessionCaisse;
import tg.parcauto.api.pointdevente.fournisseur.Fournisseur;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Depense du point de vente : salaire, loyer, electricite... Payee depuis la
 * caisse, elle cree une sortie d'especes dans la session ouverte. Une depense
 * annulee reste visible mais ne compte plus.
 */
@Entity(name = "DepensePdv")
@Table(name = "pdv_depense")
@Getter
@Setter
@NoArgsConstructor
public class DepensePdv extends BaseEntity {

    public enum Statut { VALIDE, ANNULEE }

    @Column(nullable = false, length = 40)
    private String numero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categorie_id", nullable = false)
    private CategorieDepensePdv categorie;

    @Column(nullable = false, length = 150)
    private String libelle;

    @Column(length = 150)
    private String beneficiaire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fournisseur_id")
    private Fournisseur fournisseur;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal montant;

    @Column(name = "date_depense", nullable = false)
    private LocalDate dateDepense;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModeSortie mode;

    @Column(length = 80)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private SessionCaisse session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mouvement_caisse_id")
    private MouvementCaisse mouvementCaisse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurrente_id")
    private DepenseRecurrente recurrente;

    /** Mois paye (AAAA-MM) d'une depense recurrente. */
    @Column(length = 7)
    private String periode;

    @Column(length = 500)
    private String note;

    @Column(name = "justificatif_chemin", length = 255)
    private String justificatifChemin;

    @Column(name = "justificatif_nom", length = 255)
    private String justificatifNom;

    @Column(name = "justificatif_type", length = 100)
    private String justificatifType;

    @Column(name = "auteur_nom", nullable = false, length = 150)
    private String auteurNom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.VALIDE;

    @Column(name = "annulee_le")
    private Instant annuleeLe;

    @Column(name = "annulee_par", length = 150)
    private String annuleePar;

    @Column(name = "motif_annulation", length = 255)
    private String motifAnnulation;
}
