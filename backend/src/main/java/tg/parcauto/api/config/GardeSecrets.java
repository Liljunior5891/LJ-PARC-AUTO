package tg.parcauto.api.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Refuse de demarrer avec les secrets de developpement ecrits dans
 * application.yml : une cle de signature connue permet a quiconque a le code
 * de fabriquer un jeton de super-administrateur.
 * <p>
 * Le refus est la regle : il faut declarer explicitement le developpement
 * (profil Spring « dev » ou variable PARCAUTO_DEV=true) pour demarrer avec ces
 * valeurs. Un oubli de configuration bloque donc le demarrage au lieu
 * d'exposer la plateforme.
 */
@Slf4j
@Component
public class GardeSecrets {

    /** Valeurs par defaut de application.yml, a ne jamais garder en production. */
    static final String CLE_DE_DEVELOPPEMENT = "Y2hhbmdlLW1lLXBhcmNhdXRvLWRldi1zZWNyZXQta2V5LTMyLWJ5dGVzLW1pbmltdW0=";
    public static final String MOT_DE_PASSE_ADMIN_PAR_DEFAUT = "Plateforme@2026";
    /** Adresse par defaut du super-administrateur : connue de tous, donc premiere cible. */
    static final String ADRESSE_ADMIN_PAR_DEFAUT = "superadmin@plateforme.local";

    private final Environment environnement;
    private final String cleJwt;
    private final String motDePasseAdmin;
    private final String adresseAdmin;
    private final String fournisseurPaiement;
    private final boolean developpementDeclare;

    public GardeSecrets(Environment environnement,
                        @Value("${parcauto.security.jwt.secret}") String cleJwt,
                        @Value("${parcauto.plateforme.admin.password}") String motDePasseAdmin,
                        @Value("${parcauto.plateforme.admin.email}") String adresseAdmin,
                        @Value("${parcauto.paiement.fournisseur:}") String fournisseurPaiement,
                        @Value("${PARCAUTO_DEV:false}") boolean developpementDeclare) {
        this.environnement = environnement;
        this.cleJwt = cleJwt;
        this.motDePasseAdmin = motDePasseAdmin;
        this.adresseAdmin = adresseAdmin;
        this.fournisseurPaiement = fournisseurPaiement;
        this.developpementDeclare = developpementDeclare;
    }

    @PostConstruct
    void verifier() {
        boolean developpement = (developpementDeclare || environnement.acceptsProfiles(Profiles.of("dev")))
                && !environnement.acceptsProfiles(Profiles.of("prod"));
        StringBuilder problemes = new StringBuilder();
        if (CLE_DE_DEVELOPPEMENT.equals(cleJwt) || cleJwt.getBytes(StandardCharsets.UTF_8).length < 64) {
            problemes.append(" JWT_SECRET (cle de signature des jetons) est celle de developpement ou fait moins de 64 octets ;");
        }
        if (MOT_DE_PASSE_ADMIN_PAR_DEFAUT.equals(motDePasseAdmin)) {
            problemes.append(" PLATEFORME_ADMIN_PASSWORD est celui par defaut ;");
        }
        // Paiement de demonstration : n'importe quel acheteur declarerait lui-meme sa commande payee.
        if ("simulation".equalsIgnoreCase(fournisseurPaiement == null ? "" : fournisseurPaiement.trim())) {
            problemes.append(" PAIEMENT_FOURNISSEUR=simulation (paiement de demonstration) ;");
        }
        if (ADRESSE_ADMIN_PAR_DEFAUT.equalsIgnoreCase(adresseAdmin)) {
            problemes.append(" PLATEFORME_ADMIN_EMAIL est l'adresse par defaut, facile a deviner ;");
        }
        if (problemes.isEmpty()) {
            return;
        }
        if (!developpement) {
            throw new IllegalStateException("Demarrage refuse :" + problemes
                    + " definissez des valeurs secretes propres a ce serveur"
                    + " (ou PARCAUTO_DEV=true sur un poste de developpement).");
        }
        log.error("SECRETS DE DEVELOPPEMENT EN USAGE (PARCAUTO_DEV) :{} acceptable uniquement en local.", problemes);
    }
}
