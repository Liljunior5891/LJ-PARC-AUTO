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
import tg.parcauto.api.common.EntiteAuditee;

import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "utilisateur")
@Getter
@Setter
@NoArgsConstructor
public class Utilisateur extends EntiteAuditee {

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "mot_de_passe", nullable = false, length = 100)
    private String motDePasse;

    /** Code PIN chiffre, qui deverrouille l'ecran de caisse ; nul tant qu'il n'est pas defini. */
    @Column(name = "pin_hash", length = 100)
    private String pinHash;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(length = 100)
    private String prenom;

    @Column(length = 30)
    private String telephone;

    @Column(nullable = false)
    private boolean actif = true;

    /** Version des jetons : seul un jeton de la version courante est accepte. */
    @Column(name = "version_jetons", nullable = false)
    private int versionJetons;

    /** Mot de passe provisoire : il faut le changer avant toute autre action. */
    @Column(name = "mot_de_passe_a_changer", nullable = false)
    private boolean motDePasseAChanger;

    /** Double authentification : secret chiffre de l'application d'authentification. */
    @Column(name = "totp_secret", length = 200)
    private String totpSecret;

    @Column(name = "totp_actif", nullable = false)
    private boolean totpActif;

    /** Dernier pas de temps accepte : un meme code ne sert pas deux fois. */
    @Column(name = "totp_dernier_pas")
    private Long totpDernierPas;

    /**
     * Entreprise du compte ; nulle pour l'administrateur de la plateforme.
     * <p>
     * La connexion se fait par email avant que l'entreprise soit connue : la
     * table n'est donc pas cloisonnee par Hibernate, et le service des comptes
     * restreint lui-meme chaque requete a l'entreprise courante.
     */
    @Column(name = "entreprise_id", updatable = false)
    private Long entrepriseId;

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

    /** Ferme toutes les sessions ouvertes : les jetons deja emis ne passent plus. */
    public void invaliderJetons() {
        this.versionJetons++;
    }
}
