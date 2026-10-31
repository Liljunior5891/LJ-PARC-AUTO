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
 * Cree le compte de l'administrateur de la plateforme au premier demarrage.
 * <p>
 * Ce compte n'appartient a aucune entreprise : il gere les abonnes, pas leurs
 * donnees. Les entreprises creent ensuite leur propre administrateur en
 * s'inscrivant. Le mot de passe doit etre fourni par la variable
 * d'environnement PLATEFORME_ADMIN_PASSWORD et change des la premiere connexion.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AdminInitializer {

    @Value("${parcauto.plateforme.admin.email}")
    private String email;

    @Value("${parcauto.plateforme.admin.password}")
    private String motDePasse;

    @Bean
    public ApplicationRunner creerAdministrateurPlateforme(UtilisateurRepository repository,
                                                           PasswordEncoder encoder) {
        return args -> {
            if (repository.existeAvecRole(Role.SUPER_ADMIN)) {
                return;
            }
            if (repository.existsByEmailIgnoreCase(email)) {
                log.error("Impossible de creer l'administrateur plateforme : l'adresse {} est deja utilisee "
                        + "par un compte d'entreprise. Definissez PLATEFORME_ADMIN_EMAIL.", email);
                return;
            }
            Utilisateur admin = new Utilisateur();
            admin.setEmail(email);
            admin.setMotDePasse(encoder.encode(motDePasse));
            admin.setNom("Plateforme");
            admin.setPrenom("Administrateur");
            admin.setActif(true);
            admin.setRoles(EnumSet.of(Role.SUPER_ADMIN));
            repository.save(admin);
            log.warn("Administrateur plateforme cree : {} - changez le mot de passe des la premiere connexion",
                    email);
        };
    }
}
