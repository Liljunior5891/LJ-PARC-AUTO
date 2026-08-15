package tg.parcauto.api.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.Utilisateur;
import tg.parcauto.api.security.UtilisateurRepository;

import java.util.EnumSet;

/**
 * Cree le compte administrateur initial au premier demarrage.
 * Le mot de passe doit etre fourni par la variable d'environnement ADMIN_PASSWORD
 * et change des la premiere connexion.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AdminInitializer {

    @Value("${parcauto.admin.email:admin@parcauto.tg}")
    private String email;

    @Value("${parcauto.admin.password:Admin@2026}")
    private String motDePasse;

    @Bean
    public ApplicationRunner creerAdministrateurInitial(UtilisateurRepository repository,
                                                        PasswordEncoder encoder) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            Utilisateur admin = new Utilisateur();
            admin.setEmail(email);
            admin.setMotDePasse(encoder.encode(motDePasse));
            admin.setNom("Administrateur");
            admin.setPrenom("Systeme");
            admin.setActif(true);
            admin.setRoles(EnumSet.of(Role.ADMIN));
            repository.save(admin);
            log.warn("Compte administrateur initial cree : {} - changez le mot de passe des la premiere connexion",
                    email);
        };
    }
}
