package tg.parcauto.api.agence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * Agence de l'entreprise (LJ-Agoe, LJ-Be...), avec sa propre caisse, ses floats
 * et son personnel. L'administrateur voit toutes les agences ; le superviseur et
 * le caissier ne voient que celles auxquelles ils sont rattaches.
 */
@Entity
@Table(name = "agc_agence")
@Getter
@Setter
@NoArgsConstructor
public class Agence extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String nom;

    @Column(length = 80)
    private String ville;

    @Column(length = 80)
    private String quartier;

    @Column(length = 255)
    private String adresse;

    @Column(length = 30)
    private String telephone;

    /** Superviseur responsable de l'agence ; facultatif. */
    @Column(name = "responsable_id")
    private Long responsableId;

    @Column(nullable = false)
    private boolean actif = true;

    /** Comptes rattaches a l'agence (les comptes ne sont pas cloisonnes : simples identifiants). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "agc_agence_utilisateur", joinColumns = @JoinColumn(name = "agence_id"))
    @Column(name = "utilisateur_id")
    private Set<Long> utilisateurIds = new HashSet<>();
}
