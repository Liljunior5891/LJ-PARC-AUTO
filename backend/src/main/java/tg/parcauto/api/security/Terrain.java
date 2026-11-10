package tg.parcauto.api.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Le chauffeur sur le terrain : il agit sur SES missions, SA livraison et SON
 * vehicule, jamais sur ceux d'un autre. Le gestionnaire et l'administrateur
 * gardent la main sur tout.
 */
public final class Terrain {

    private Terrain() {
    }

    public static UtilisateurPrincipal courant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UtilisateurPrincipal p ? p : null;
    }

    /** Vrai pour un compte qui n'a que le profil chauffeur (ni gestionnaire, ni administrateur). */
    public static boolean chauffeurSeul() {
        UtilisateurPrincipal p = courant();
        if (p == null) {
            return false;
        }
        Set<String> roles = p.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        return roles.contains("ROLE_CHAUFFEUR") && !roles.contains("ROLE_ADMIN") && !roles.contains("ROLE_GESTIONNAIRE_PARC");
    }

    /** Fiche chauffeur du compte connecte (nulle si le compte n'est relie a aucune). */
    public static Long chauffeurId() {
        UtilisateurPrincipal p = courant();
        return p == null ? null : p.getChauffeurId();
    }

    /** Un chauffeur seul n'agit que sur ce qui lui est confie. */
    public static void exigerLeSien(Long chauffeurConfie, String quoi) {
        if (!chauffeurSeul()) {
            return;
        }
        Long moi = chauffeurId();
        if (moi == null || chauffeurConfie == null || !moi.equals(chauffeurConfie)) {
            throw new AccessDeniedException(quoi + " est confie(e) a un autre chauffeur");
        }
    }
}
