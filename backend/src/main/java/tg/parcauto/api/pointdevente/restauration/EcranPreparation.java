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
 * Ecran de preparation cree par le gerant (« Grill », « Bar », « Patisserie ») :
 * il recoit les articles commandes dans les categories choisies, et seuls ses
 * utilisateurs l'ouvrent (le gerant et l'administrateur les ouvrent tous).
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
