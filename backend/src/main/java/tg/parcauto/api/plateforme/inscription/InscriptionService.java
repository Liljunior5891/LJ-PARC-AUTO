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
        // La transaction s'ouvre dans le mode systeme : la session Hibernate fixe son entreprise a l'ouverture.
        return ContexteEntreprise.enModeSysteme(
                () -> transaction.execute(statut -> creer(demande, joursEssai, creeParPlateforme)));
    }

    private String creer(Demande demande, Integer joursEssai, boolean creeParPlateforme) {
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
        admin.setMotDePasse(encodeur.encode(demande.motDePasse()));
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
