package tg.parcauto.api.security;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.parcauto.api.common.BaseEntity;

import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "utilisateur")
@Getter
@Setter
@NoArgsConstructor
public class Utilisateur extends BaseEntity {

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "mot_de_passe", nullable = false, length = 100)
    private String motDePasse;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(length = 100)
    private String prenom;

    @Column(length = 30)
    private String telephone;

    @Column(nullable = false)
    private boolean actif = true;

    /** Lien optionnel vers la fiche chauffeur, pour les comptes de profil CHAUFFEUR. */
    @Column(name = "chauffeur_id")
    private Long chauffeurId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "utilisateur_role", joinColumns = @JoinColumn(name = "utilisateur_id"))
    @Column(name = "role", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    public String nomComplet() {
        return prenom == null || prenom.isBlank() ? nom : prenom + " " + nom;
    }
}
