package tg.parcauto.api.agence.tresorerie;

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

/**
 * Ecriture du registre : une operation et ses lignes, une par compte touche.
 * Ni l'ecriture ni ses lignes ne se modifient (la base le refuse) : une
 * correction passe par une ecriture inverse qui nomme celle qu'elle annule.
 */
@Entity
@Table(name = "agc_ecriture")
@Getter
@Setter
@NoArgsConstructor
public class Ecriture extends BaseEntity {

    @Column(name = "agence_id", nullable = false, updatable = false)
    private Long agenceId;

    @Column(nullable = false, length = 40, updatable = false)
    private String numero;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private NatureEcriture nature;

    @Column(name = "date_operation", nullable = false, updatable = false)
    private Instant dateOperation;

    @Column(nullable = false, updatable = false)
    private LocalDate journee;

    @Column(nullable = false, length = 255, updatable = false)
    private String libelle;

    @Column(length = 500, updatable = false)
    private String motif;

    @Column(name = "origine_type", length = 40, updatable = false)
    private String origineType;

    @Column(name = "origine_id", updatable = false)
    private Long origineId;

    @Column(name = "ecriture_annulee_id", updatable = false)
    private Long ecritureAnnuleeId;

    @Column(name = "auteur_id", updatable = false)
    private Long auteurId;

    @Column(name = "auteur_nom", length = 150, updatable = false)
    private String auteurNom;
}
