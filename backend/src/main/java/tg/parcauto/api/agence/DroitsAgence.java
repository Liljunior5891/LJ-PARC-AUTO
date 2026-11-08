package tg.parcauto.api.agence;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.security.Role;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Droits de l'utilisateur courant dans le module Agences, verifies cote serveur
 * ({@code @PreAuthorize("@droitsAgence.a('PARAMETRAGE')")}). L'administrateur a
 * tous les droits ; ceux du superviseur et du caissier viennent du reglage de
 * l'entreprise, ou des valeurs par defaut tant que le module n'a pas ete regle.
 */
@Component("droitsAgence")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DroitsAgence {

    private final DroitAgenceRepository droits;
    private final ReglageAgenceRepository reglages;

    public boolean a(String permission) {
        return courants().contains(PermissionAgence.valueOf(permission));
    }

    public void exiger(PermissionAgence permission) {
        if (!courants().contains(permission)) {
            throw new AccessDeniedException("Vous n'avez pas le droit : " + permission.getLibelle());
        }
    }

    /** Droits de l'utilisateur connecte. */
    public Set<PermissionAgence> courants() {
        Set<Role> roles = rolesCourants();
        if (roles.contains(Role.ADMIN)) {
            return EnumSet.allOf(PermissionAgence.class);
        }
        Set<Role> reglables = roles.stream().filter(PermissionAgence.PROFILS_REGLABLES::contains)
                .collect(Collectors.toSet());
        if (reglables.isEmpty()) {
            return EnumSet.noneOf(PermissionAgence.class);
        }
        Set<PermissionAgence> accordes = EnumSet.noneOf(PermissionAgence.class);
        if (reglages.findFirstByOrderByIdAsc().isEmpty()) {
            for (PermissionAgence p : PermissionAgence.values()) {
                if (p.getParDefaut().stream().anyMatch(reglables::contains)) {
                    accordes.add(p);
                }
            }
        } else {
            droits.findByRoleIn(reglables).forEach(d -> accordes.add(d.getPermission()));
        }
        return accordes;
    }

    public static boolean estAdministrateur() {
        return rolesCourants().contains(Role.ADMIN);
    }

    private static Set<Role> rolesCourants() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Set<Role> roles = EnumSet.noneOf(Role.class);
        if (auth == null) {
            return roles;
        }
        for (GrantedAuthority a : auth.getAuthorities()) {
            String nom = a.getAuthority().replaceFirst("^ROLE_", "");
            for (Role r : Role.values()) {
                if (r.name().equals(nom)) {
                    roles.add(r);
                }
            }
        }
        return roles;
    }
}
