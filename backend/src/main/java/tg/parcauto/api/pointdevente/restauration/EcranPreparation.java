package tg.parcauto.api.pointdevente.restauration;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;
import tg.parcauto.api.pointdevente.catalogue.Categorie;

import java.util.HashSet;
import java.util.Set;

/**
 * Poste de preparation cree par le gerant (« Grill », « Bar », « Patisserie ») :
 * il recoit les articles commandes dans les categories choisies, sur un ecran
 * que seuls ses utilisateurs ouvrent (le gerant et l'administrateur les ouvrent
 * tous), sur une imprimante de cuisine, ou les deux.
 */
@Entity
@Table(name = "pdv_ecran_preparation")
@Getter
@Setter
@NoArgsConstructor
public class EcranPreparation extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String nom;

    @Column(nullable = false)
    private boolean actif = true;

    /** Faux pour un poste qui ne fait qu'imprimer : il n'apparait pas parmi les ecrans a ouvrir. */
    @Column(name = "afficher_ecran", nullable = false)
    private boolean afficherEcran = true;

    /** Imprimante qui recoit les bons du poste ; nulle : pas d'impression. */
    @jakarta.persistence.ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imprimante_id")
    private tg.parcauto.api.pointdevente.impression.Imprimante imprimante;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "pdv_ecran_categorie",
            joinColumns = @JoinColumn(name = "ecran_id"),
            inverseJoinColumns = @JoinColumn(name = "categorie_id"))
    private Set<Categorie> categories = new HashSet<>();

    /** Comptes autorises a ouvrir l'ecran (les comptes ne sont pas cloisonnes : simples identifiants). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "pdv_ecran_utilisateur", joinColumns = @JoinColumn(name = "ecran_id"))
    @Column(name = "utilisateur_id")
    private Set<Long> utilisateurIds = new HashSet<>();
}
