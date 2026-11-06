package tg.parcauto.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

@Configuration
public class AuditConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
                return Optional.of("systeme");
            }
            // Prenom et nom de l'auteur, tels que les rapports les affichent (et non l'adresse e-mail).
            if (auth.getPrincipal() instanceof tg.parcauto.api.security.UtilisateurPrincipal p
                    && p.getNomComplet() != null && !p.getNomComplet().isBlank()) {
                String nom = p.getNomComplet().trim();
                return Optional.of(nom.length() > 100 ? nom.substring(0, 100) : nom);
            }
            return Optional.ofNullable(auth.getName());
        };
    }
}
