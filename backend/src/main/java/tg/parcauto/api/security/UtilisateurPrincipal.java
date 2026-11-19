package tg.parcauto.api.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class UtilisateurPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String motDePasse;
    private final String nomComplet;
    private final Long chauffeurId;
    private final Long entrepriseId;
    private final String entrepriseNom;
    private final boolean actif;
    private final int versionJetons;
    private final boolean motDePasseAChanger;
    private final boolean deuxFacteursActif;
    private final List<GrantedAuthority> authorities;
    /**
     * Mode d'assistance (CONSULTATION ou INTERVENTION) quand le super-administrateur
     * travaille dans l'espace d'une entreprise ; nul pour une session ordinaire.
     */
    private final String assistance;

    /**
     * @param entrepriseNom nom de l'entreprise, nul pour l'administrateur de la plateforme
     * @param entrepriseActive faux si la plateforme a suspendu l'entreprise : le compte est alors bloque
     */
    public UtilisateurPrincipal(Utilisateur u, String entrepriseNom, boolean entrepriseActive) {
        this.id = u.getId();
        this.email = u.getEmail();
        this.motDePasse = u.getMotDePasse();
        this.nomComplet = u.nomComplet();
        this.chauffeurId = u.getChauffeurId();
        this.entrepriseId = u.getEntrepriseId();
        this.entrepriseNom = entrepriseNom;
        this.actif = u.isActif() && entrepriseActive;
        this.versionJetons = u.getVersionJetons();
        this.motDePasseAChanger = u.isMotDePasseAChanger();
        this.deuxFacteursActif = u.isTotpActif();
        this.authorities = u.getRoles().stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority(r.authority()))
                .toList();
        this.assistance = null;
    }

    private UtilisateurPrincipal(UtilisateurPrincipal base, Long entrepriseId, String entrepriseNom, String mode) {
        this.id = base.id;
        this.email = base.email;
        this.motDePasse = base.motDePasse;
        // Nom ecrit dans les journaux et sur les fiches (cree_par) : l'entreprise voit qui est intervenu.
        this.nomComplet = "Assistance Nova (" + base.nomComplet + ")";
        this.chauffeurId = null;
        this.entrepriseId = entrepriseId;
        this.entrepriseNom = entrepriseNom;
        this.actif = base.actif;
        this.versionJetons = base.versionJetons;
        this.motDePasseAChanger = false;
        this.deuxFacteursActif = base.deuxFacteursActif;
        this.authorities = List.of(new SimpleGrantedAuthority(Role.ADMIN.authority()));
        this.assistance = mode;
    }

    /**
     * Le super-administrateur dans l'espace d'une entreprise : administrateur de
     * cette seule entreprise, sans aucun droit de la plateforme.
     */
    public static UtilisateurPrincipal pourAssistance(UtilisateurPrincipal superAdmin, Long entrepriseId,
                                                      String entrepriseNom, String mode) {
        return new UtilisateurPrincipal(superAdmin, entrepriseId, entrepriseNom, mode);
    }

    public boolean enAssistance() {
        return assistance != null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return motDePasse;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return actif;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /** Le jeton date-t-il d'avant la derniere fermeture des sessions du compte ? (sans version : 0) */
    public boolean jetonPerime(Object versionDuJeton) {
        int version = versionDuJeton instanceof Number n ? n.intValue() : 0;
        return version != versionJetons;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
