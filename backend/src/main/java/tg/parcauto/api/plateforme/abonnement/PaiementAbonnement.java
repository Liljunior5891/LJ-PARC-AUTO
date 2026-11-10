package tg.parcauto.api.plateforme.abonnement;

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
import tg.parcauto.api.common.EntiteAuditee;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * Paiement d'abonnement : declare par l'entreprise (en attente) puis valide ou
 * rejete par le super-administrateur, ou saisi par lui directement. La
 * validation ouvre la periode et attribue le numero de facture.
 */
@Entity
@Table(name = "abo_paiement")
@Getter
@Setter
@NoArgsConstructor
public class PaiementAbonnement extends EntiteAuditee {

    @Column(name = "entreprise_id", nullable = false, updatable = false)
    private Long entrepriseId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "formule_id")
    private Formule formule;

    /** Nom de la formule au moment du paiement, pour la facture. */
    @Column(name = "formule_nom", nullable = false, length = 60)
    private String formuleNom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Periodicite periodicite;

    @Column(nullable = false, length = 300)
    private String modules;

    /** Prix mensuel de chaque module au moment du paiement, pour la facture : MODULE=prix;... */
    @Column(length = 500)
    private String detail;

    @Column(nullable = false)
    private BigDecimal montant;

    @Column(nullable = false, length = 10)
    private String devise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MoyenPaiementAbonnement moyen;

    @Column(length = 100)
    private String reference;

    @Column(length = 500)
    private String note;

    @Column(name = "preuve_chemin", length = 300)
    private String preuveChemin;

    @Column(name = "preuve_nom", length = 200)
    private String preuveNom;

    @Column(name = "preuve_type", length = 100)
    private String preuveType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutPaiementAbonnement statut = StatutPaiementAbonnement.EN_ATTENTE;

    @Column(name = "motif_rejet", length = 300)
    private String motifRejet;

    @Column(name = "saisi_plateforme", nullable = false)
    private boolean saisiPlateforme;

    @Column(name = "traite_par", length = 100)
    private String traitePar;

    @Column(name = "traite_le")
    private Instant traiteLe;

    @Column(name = "periode_debut")
    private LocalDate periodeDebut;

    @Column(name = "periode_fin")
    private LocalDate periodeFin;

    @Column(name = "numero_facture", length = 30)
    private String numeroFacture;

    public Set<ModuleApplicatif> modulesListe() {
        return Abonnement.lireModules(modules);
    }

    /** Prix mensuel de chaque module paye, dans l'ordre des modules. */
    public java.util.Map<ModuleApplicatif, BigDecimal> detailPrix() {
        java.util.Map<ModuleApplicatif, BigDecimal> prix = new java.util.EnumMap<>(ModuleApplicatif.class);
        if (detail != null && !detail.isBlank()) {
            for (String part : detail.split(";")) {
                String[] cv = part.split("=");
                if (cv.length == 2) {
                    prix.put(ModuleApplicatif.valueOf(cv[0].trim()), new BigDecimal(cv[1].trim()));
                }
            }
        }
        return prix;
    }
}
