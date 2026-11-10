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
    private final List<GrantedAuthority> authorities;

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
        this.authorities = u.getRoles().stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority(r.authority()))
                .toList();
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
