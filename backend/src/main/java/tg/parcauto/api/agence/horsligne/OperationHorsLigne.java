package tg.parcauto.api.agence.horsligne;

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
 * Operation d'un caissier d'agence saisie sans connexion, puis envoyee au
 * serveur. Sa cle, tiree par l'appareil, evite de l'enregistrer deux fois si
 * l'envoi est repete. Refusee par le serveur (float insuffisant, caisse fermee,
 * carte deja vendue...), elle devient un conflit : l'argent a deja change de
 * main, seul le superviseur decide de la suite.
 */
@Entity
@Table(name = "agc_operation_hors_ligne")
@Getter
@Setter
@NoArgsConstructor
public class OperationHorsLigne extends BaseEntity {

    public enum Type { TRANSACTION, VENTE_CARTE, DEPENSE }

    /** REPRISE : conflit regle en rejouant l'operation ; ABANDONNEE : regle hors du logiciel. */
    public enum Statut { ENREGISTREE, CONFLIT, REPRISE, ABANDONNEE }

    @Column(nullable = false, length = 64, updatable = false)
    private String cle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Type type;

    @Column(name = "agence_id", updatable = false)
    private Long agenceId;

    @Column(name = "auteur_id", nullable = false, updatable = false)
    private Long auteurId;

    @Column(name = "auteur_nom", nullable = false, length = 150, updatable = false)
    private String auteurNom;

    @Column(name = "saisie_le", nullable = false, updatable = false)
    private Instant saisieLe;

    @Column(name = "recue_le", nullable = false, updatable = false)
    private Instant recueLe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Statut statut;

    /** Ce que l'operation represente, en clair : « Depot Mixx by Yas 25 000 F ». */
    @Column(nullable = false, length = 255, updatable = false)
    private String resume;

    /** Saisie d'origine (JSON), pour la rejouer. */
    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String donnees;

    /** Reference de ce qui a ete enregistre (transaction, vente, depense). */
    @Column(length = 40)
    private String reference;

    @Column(length = 1000)
    private String motif;

    @Column(name = "traitee_par_nom", length = 150)
    private String traiteeParNom;

    @Column(name = "traitee_le")
    private Instant traiteeLe;

    @Column(name = "note_traitement", length = 500)
    private String noteTraitement;
}
