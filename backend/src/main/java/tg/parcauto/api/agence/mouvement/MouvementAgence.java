package tg.parcauto.api.agence.mouvement;

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

/**
 * Mouvement entre comptes d'une agence, ou regularisation d'un compte. Il ne
 * touche les soldes qu'une fois valide ; il ne cree ni recette ni depense.
 */
@Entity
@Table(name = "agc_mouvement")
@Getter
@Setter
@NoArgsConstructor
public class MouvementAgence extends BaseEntity {

    public enum Type {
        APPROVISIONNEMENT("Approvisionnement"),
        TRANSFERT_INTERNE("Transfert interne"),
        VERSEMENT_BANCAIRE("Versement bancaire"),
        RETRAIT_BANCAIRE("Retrait bancaire"),
        CREDIT_COMMISSION("Credit de commission"),
        REGULARISATION("Regularisation"),
        AUTRE("Autre mouvement");

        private final String libelle;

        Type(String libelle) {
            this.libelle = libelle;
        }

        public String getLibelle() {
            return libelle;
        }
    }

    public enum Statut { EN_ATTENTE, VALIDE, REJETE, ANNULE }

    @Column(name = "agence_id", nullable = false, updatable = false)
    private Long agenceId;

    @Column(nullable = false, length = 40, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private Type type;

    @Column(name = "compte_source_id", updatable = false)
    private Long compteSourceId;

    @Column(name = "compte_destination_id", updatable = false)
    private Long compteDestinationId;

    @Column(nullable = false, updatable = false)
    private long montant;

    @Column(nullable = false, length = 255, updatable = false)
    private String motif;

    @Column(length = 1000, updatable = false)
    private String justification;

    @Column(name = "piece_chemin", length = 255)
    private String pieceChemin;

    @Column(name = "piece_nom", length = 255)
    private String pieceNom;

    @Column(name = "piece_type", length = 100)
    private String pieceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut;

    @Column(name = "date_mouvement", nullable = false, updatable = false)
    private Instant dateMouvement;

    @Column(name = "auteur_id", nullable = false, updatable = false)
    private Long auteurId;

    @Column(name = "auteur_nom", nullable = false, length = 150, updatable = false)
    private String auteurNom;

    @Column(name = "valide_le")
    private Instant valideLe;

    @Column(name = "valide_par_id")
    private Long valideParId;

    @Column(name = "valide_par_nom", length = 150)
    private String valideParNom;

    @Column(name = "motif_rejet", length = 500)
    private String motifRejet;

    @Column(name = "ecriture_id")
    private Long ecritureId;

    @Column(name = "annulation_ecriture_id")
    private Long annulationEcritureId;

    @Column(name = "motif_annulation", length = 500)
    private String motifAnnulation;
}
