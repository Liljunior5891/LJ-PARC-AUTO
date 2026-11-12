package tg.parcauto.api.plateforme.inscription;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.parametrage.Societe;
import tg.parcauto.api.parametrage.SocieteRepository;
import tg.parcauto.api.plateforme.entreprise.Entreprise;
import tg.parcauto.api.plateforme.entreprise.EntrepriseRepository;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleService;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.Utilisateur;
import tg.parcauto.api.security.UtilisateurRepository;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static tg.parcauto.api.security.UtilisateurDto.LONGUEUR_MOT_DE_PASSE;

/**
 * Creation d'un compte entreprise en libre-service.
 * <p>
 * En une transaction : l'entreprise, son parametrage initial, son premier
 * administrateur et les modules choisis. L'entreprise n'existant pas encore
 * dans le contexte, le traitement s'execute en mode systeme et pose lui-meme
 * l'entreprise sur les donnees cloisonnees qu'il cree.
 */
@Service
@RequiredArgsConstructor
public class InscriptionService {

    private final EntrepriseRepository entrepriseRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final SocieteRepository societeRepository;
    private final ModuleService moduleService;
    private final tg.parcauto.api.plateforme.abonnement.AbonnementService abonnementService;
    private final PasswordEncoder encodeur;
    private final TransactionTemplate transaction;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final tg.parcauto.api.common.notification.NotificationService notifications;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    @org.springframework.beans.factory.annotation.Value("${parcauto.notifications.url-application:http://localhost:4300}")
    private String urlApplication;

    /** Duree de validite du lien de confirmation. */
    private static final java.time.Duration VALIDITE_LIEN = java.time.Duration.ofHours(24);

    public record Demande(
            @NotBlank @Size(max = 150) String nomEntreprise,
            @Size(max = 100) String pays,
            @Size(max = 40) String telephone,
            @NotBlank @Size(max = 100) String nom,
            @Size(max = 100) String prenom,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(min = LONGUEUR_MOT_DE_PASSE, max = 72) String motDePasse,
            /** Modules a activer d'emblee ; vide, l'entreprise les choisira apres connexion. */
            List<ModuleApplicatif> modules,
            /** Type d'activite des modules qui se declinent ; absent, celui par defaut. */
            Map<ModuleApplicatif, String> variantes) {
    }

    /** @return l'adresse du compte administrateur cree, pour ouvrir sa session */
    public String inscrire(Demande demande) {
        return inscrire(demande, null);
    }

    /** Creation par le super-administrateur : la duree d'essai peut differer du reglage (nulle = reglage). */
    public String inscrire(Demande demande, Integer joursEssai) {
        return inscrire(demande, joursEssai, false);
    }

    /**
     * @param creeParPlateforme vrai quand l'exploitant saisit le mot de passe pour le client :
     *                          il est alors provisoire, a changer a la premiere connexion.
     */
    public String inscrire(Demande demande, Integer joursEssai, boolean creeParPlateforme) {
        return inscrire(demande, joursEssai, creeParPlateforme, false);
    }

    private String inscrire(Demande demande, Integer joursEssai, boolean creeParPlateforme, boolean motDePasseChiffre) {
        // La transaction s'ouvre dans le mode systeme : la session Hibernate fixe son entreprise a l'ouverture.
        return ContexteEntreprise.enModeSysteme(
                () -> transaction.execute(statut -> creer(demande, joursEssai, creeParPlateforme, motDePasseChiffre)));
    }

    // ------------------------------------------------- inscription en libre-service

    /**
     * Premiere etape : la demande est gardee en attente et un lien de confirmation part
     * a l'adresse donnee. Meme effet visible que l'adresse ait deja un compte ou non : la
     * personne concernee recoit alors un e-mail « vous avez deja un compte ». Rien
     * n'indique a un tiers si une adresse est inscrite.
     */
    public void demander(Demande demande) {
        String email = demande.email().trim();
        if (utilisateurRepository.existsByEmailIgnoreCase(email)) {
            notifications.email(null, email, "Vous avez deja un compte",
                    "Bonjour,\n\nUne inscription vient d'etre demandee avec cette adresse, qui a deja un compte.\n"
                            + "Connectez-vous ici :\n" + urlApplication + "/connexion\n\n"
                            + "Si ce n'etait pas vous, ignorez ce message : rien n'a ete modifie.\n" + signature(),
                    "INSCRIPTION_EXISTANTE");
            return;
        }
        byte[] alea = new byte[32];
        new java.security.SecureRandom().nextBytes(alea);
        String jeton = java.util.HexFormat.of().formatHex(alea);
        Demande gardee = new Demande(demande.nomEntreprise(), demande.pays(), demande.telephone(), demande.nom(),
                demande.prenom(), email, encodeur.encode(demande.motDePasse()), demande.modules(), demande.variantes());
        String donnees;
        try {
            donnees = json.writeValueAsString(gardee);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        jdbc.update("insert into inscription_attente (email, jeton_empreinte, donnees, expire_le) values (?, ?, ?, ?)",
                email, empreinte(jeton), donnees, java.sql.Timestamp.from(java.time.Instant.now().plus(VALIDITE_LIEN)));
        notifications.email(null, email, "Confirmez la creation du compte " + demande.nomEntreprise().trim(),
                "Bonjour " + demande.nom().trim() + ",\n\n"
                        + "Pour creer le compte de " + demande.nomEntreprise().trim() + ", confirmez votre adresse en"
                        + " ouvrant ce lien (valable 24 heures) :\n"
                        + urlApplication + "/inscription/confirmation?jeton=" + jeton + "\n\n"
                        + "Si vous n'avez rien demande, ignorez ce message : aucun compte ne sera cree.\n" + signature(),
                "VERIFICATION_EMAIL");
    }

    /**
     * Seconde etape : le lien est ouvert, le compte est cree avec la demande gardee.
     *
     * @return l'adresse du compte administrateur cree, pour ouvrir sa session
     */
    public String confirmer(String jeton) {
        if (jeton == null || !jeton.matches("^[0-9a-f]{64}$")) {
            throw new BusinessException("Lien de confirmation invalide");
        }
        java.util.List<java.util.Map<String, Object>> lignes = jdbc.queryForList("""
                select id, donnees from inscription_attente
                where jeton_empreinte = ? and utilisee_le is null and expire_le > now()
                """, empreinte(jeton));
        if (lignes.isEmpty()) {
            throw new BusinessException("Ce lien a expire ou a deja servi : recommencez l'inscription,"
                    + " ou connectez-vous si le compte est deja cree");
        }
        Demande demande;
        try {
            demande = json.readValue((String) lignes.get(0).get("donnees"), Demande.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BusinessException("Demande d'inscription illisible : recommencez l'inscription");
        }
        // Si deux liens sont ouverts en meme temps, le second echoue sur l'adresse deja prise.
        String email = inscrire(demande, null, false, true);
        jdbc.update("update inscription_attente set utilisee_le = now() where id = ?", lignes.get(0).get("id"));
        return email;
    }

    /** Demandes jamais confirmees : effacees une semaine apres (elles contiennent des donnees personnelles). */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 15 4 * * *")
    public void purgerDemandes() {
        jdbc.update("delete from inscription_attente where cree_le < now() - interval '7 days'");
    }

    private static String empreinte(String jeton) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(jeton.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String signature() {
        return "\n-- \nLJ SaaS\nMessage automatique : merci de ne pas y repondre.\n";
    }

    private String creer(Demande demande, Integer joursEssai, boolean creeParPlateforme, boolean motDePasseChiffre) {
        String email = demande.email().trim();
        if (utilisateurRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("Cette adresse ne peut pas servir a une nouvelle inscription."
                    + " Si vous avez deja un compte, connectez-vous ; sinon utilisez une autre adresse.");
        }

        Entreprise entreprise = new Entreprise();
        entreprise.setNom(demande.nomEntreprise().trim());
        entreprise.setPays(vide(demande.pays()));
        entreprise.setTelephone(vide(demande.telephone()));
        entreprise.setEmail(email);
        entreprise = entrepriseRepository.save(entreprise);

        Societe societe = new Societe();
        societe.setEntrepriseId(entreprise.getId());
        societe.setNom(entreprise.getNom());
        societe.setPays(entreprise.getPays());
        societe.setTelephone(entreprise.getTelephone());
        societe.setEmail(email);
        societeRepository.save(societe);

        Utilisateur admin = new Utilisateur();
        admin.setEntrepriseId(entreprise.getId());
        admin.setEmail(email);
        admin.setNom(demande.nom().trim());
        admin.setPrenom(vide(demande.prenom()));
        admin.setTelephone(vide(demande.telephone()));
        admin.setMotDePasse(motDePasseChiffre ? demande.motDePasse() : encodeur.encode(demande.motDePasse()));
        // Saisi par l'exploitant : l'administrateur du client le remplace a sa premiere connexion.
        admin.setMotDePasseAChanger(creeParPlateforme);
        admin.setRoles(EnumSet.of(Role.ADMIN));
        admin.setActif(true);
        utilisateurRepository.save(admin);
        abonnementService.ouvrirEssai(entreprise.getId(), joursEssai);

        if (demande.modules() != null) {
            for (ModuleApplicatif module : demande.modules()) {
                moduleService.activer(entreprise.getId(), module,
                        demande.variantes() == null ? null : demande.variantes().get(module));
            }
        }
        return email;
    }

    private String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }
}
