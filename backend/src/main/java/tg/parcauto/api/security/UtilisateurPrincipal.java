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
        return actif;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
