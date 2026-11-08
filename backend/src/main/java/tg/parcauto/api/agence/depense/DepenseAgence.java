package tg.parcauto.api.agence.depense;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.time.Instant;
import java.time.LocalDate;

/** Depense d'une agence : une charge, payee depuis un compte de l'agence par le registre. */
@Entity
@Table(name = "agc_depense")
@Getter
@Setter
@NoArgsConstructor
public class DepenseAgence extends BaseEntity {

    public enum Statut { VALIDEE, ANNULEE }

    @Column(name = "agence_id", nullable = false, updatable = false)
    private Long agenceId;

    @Column(nullable = false, length = 40, updatable = false)
    private String numero;

    @Column(name = "categorie_id", nullable = false)
    private Long categorieId;

    @Column(nullable = false, length = 255)
    private String libelle;

    @Column(nullable = false, updatable = false)
    private long montant;

    @Column(nullable = false, updatable = false)
    private LocalDate journee;

    @Column(name = "date_depense", nullable = false, updatable = false)
    private Instant dateDepense;

    @Column(name = "compte_id", nullable = false, updatable = false)
    private Long compteId;

    @Column(length = 150)
    private String beneficiaire;

    @Column(length = 80)
    private String reference;

    @Column(name = "recurrente_id", updatable = false)
    private Long recurrenteId;

    @Column(length = 7, updatable = false)
    private String periode;

    @Column(length = 500)
    private String observations;

    @Column(name = "justificatif_chemin", length = 255)
    private String justificatifChemin;

    @Column(name = "justificatif_nom", length = 255)
    private String justificatifNom;

    @Column(name = "justificatif_type", length = 100)
    private String justificatifType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut = Statut.VALIDEE;

    @Column(name = "auteur_id", nullable = false, updatable = false)
    private Long auteurId;

    @Column(name = "auteur_nom", nullable = false, length = 150, updatable = false)
    private String auteurNom;

    @Column(name = "ecriture_id")
    private Long ecritureId;

    @Column(name = "annulation_ecriture_id")
    private Long annulationEcritureId;

    @Column(name = "annulee_le")
    private Instant annuleeLe;

    @Column(name = "annulee_par_nom", length = 150)
    private String annuleeParNom;

    @Column(name = "motif_annulation", length = 500)
    private String motifAnnulation;
}
