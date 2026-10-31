package tg.parcauto.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tg.parcauto.api.plateforme.module.ModuleService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compose la reponse de connexion, commune a la connexion et a l'inscription. */
@Service
@RequiredArgsConstructor
public class SessionService {

    private final JwtService jwtService;
    private final ModuleService moduleService;

    public AuthController.LoginResponse ouvrir(UtilisateurPrincipal principal) {
        return new AuthController.LoginResponse(
                jwtService.genererToken(principal), jwtService.expirationSecondes(), profil(principal));
    }

    public AuthController.UtilisateurCourant profil(UtilisateurPrincipal p) {
        List<String> roles = p.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .toList();
        List<String> modules = moduleService.actifs(p.getEntrepriseId()).stream()
                .map(Enum::name)
                .toList();
        Map<String, String> variantes = new LinkedHashMap<>();
        moduleService.variantes(p.getEntrepriseId()).forEach((module, variante) -> variantes.put(module.name(), variante));
        return new AuthController.UtilisateurCourant(
                p.getId(), p.getEmail(), p.getNomComplet(), p.getChauffeurId(), roles,
                p.getEntrepriseId(), p.getEntrepriseNom(), modules, variantes);
    }
}
