package tg.parcauto.api.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Refuse de demarrer en production avec les secrets de developpement ecrits
 * dans application.yml : une cle de signature connue permet a quiconque a le
 * code de fabriquer un jeton de super-administrateur. En developpement, un
 * avertissement suffit.
 * <p>
 * Production : profil Spring « prod » ou variable PARCAUTO_PRODUCTION=true.
 */
@Slf4j
@Component
public class GardeSecrets {

    /** Valeurs par defaut de application.yml, a ne jamais garder en production. */
    static final String CLE_DE_DEVELOPPEMENT = "Y2hhbmdlLW1lLXBhcmNhdXRvLWRldi1zZWNyZXQta2V5LTMyLWJ5dGVzLW1pbmltdW0=";
    public static final String MOT_DE_PASSE_ADMIN_PAR_DEFAUT = "Plateforme@2026";

    private final Environment environnement;
    private final String cleJwt;
    private final String motDePasseAdmin;
    private final boolean productionDeclaree;

    public GardeSecrets(Environment environnement,
                        @Value("${parcauto.security.jwt.secret}") String cleJwt,
                        @Value("${parcauto.plateforme.admin.password}") String motDePasseAdmin,
                        @Value("${PARCAUTO_PRODUCTION:false}") boolean productionDeclaree) {
        this.environnement = environnement;
        this.cleJwt = cleJwt;
        this.motDePasseAdmin = motDePasseAdmin;
        this.productionDeclaree = productionDeclaree;
    }

    @PostConstruct
    void verifier() {
        boolean production = productionDeclaree || environnement.acceptsProfiles(Profiles.of("prod"));
        StringBuilder problemes = new StringBuilder();
        if (CLE_DE_DEVELOPPEMENT.equals(cleJwt) || cleJwt.getBytes(StandardCharsets.UTF_8).length < 32) {
            problemes.append(" JWT_SECRET (cle de signature des jetons) est celle de developpement ou trop courte ;");
        }
        if (MOT_DE_PASSE_ADMIN_PAR_DEFAUT.equals(motDePasseAdmin)) {
            problemes.append(" PLATEFORME_ADMIN_PASSWORD est celui par defaut ;");
        }
        if (problemes.isEmpty()) {
            return;
        }
        if (production) {
            throw new IllegalStateException("Demarrage refuse en production :" + problemes
                    + " definissez des valeurs secretes propres a ce serveur.");
        }
        log.error("SECRETS DE DEVELOPPEMENT EN USAGE :{} acceptable uniquement en local.", problemes);
    }
}
