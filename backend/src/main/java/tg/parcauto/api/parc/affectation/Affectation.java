package tg.parcauto.api.parc.affectation;

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
import tg.parcauto.api.parc.camion.Camion;
import tg.parcauto.api.parc.chauffeur.Chauffeur;

import java.time.LocalDate;

/** Affectation d'un chauffeur a un camion, chapitre 9 du cahier des charges. */
@Entity
@Table(name = "affectation")
@Getter
@Setter
@NoArgsConstructor
public class Affectation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "camion_id", nullable = false)
    private Camion camion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chauffeur_id", nullable = false)
    private Chauffeur chauffeur;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    /** Nulle tant que l'affectation est en cours. */
    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(length = 255)
    private String motif;

    @Column(columnDefinition = "text")
    private String observations;

    public boolean enCours() {
        return dateFin == null;
    }

    /** Vrai si l'affectation chevauche la periode [debut, fin] (fin nulle = ouverte). */
    public boolean chevauche(LocalDate debut, LocalDate fin) {
        LocalDate finCourante = this.dateFin == null ? LocalDate.MAX : this.dateFin;
        LocalDate finTestee = fin == null ? LocalDate.MAX : fin;
        return !debut.isAfter(finCourante) && !this.dateDebut.isAfter(finTestee);
    }
}
