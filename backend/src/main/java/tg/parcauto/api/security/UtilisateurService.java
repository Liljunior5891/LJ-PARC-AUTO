package tg.parcauto.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleService;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Gestion des comptes utilisateurs (chapitre 40).
 * <p>
 * Deux garde-fous structurent ce service : on ne se retire jamais soi-meme des
 * moyens de se reconnecter, et l'entreprise ne peut pas se retrouver sans
 * administrateur actif.
 * <p>
 * La table des comptes n'etant pas cloisonnee par Hibernate (voir
 * {@link Utilisateur#getEntrepriseId()}), chaque lecture passe ici par
 * l'entreprise courante : un administrateur ne voit et ne modifie que les
 * comptes de son entreprise.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UtilisateurService {

    private final tg.parcauto.api.plateforme.abonnement.AbonnementService abonnement;

    private final UtilisateurRepository repository;
    private final PasswordEncoder encodeur;
    private final ModuleService moduleService;
    private final tg.parcauto.api.pointdevente.reglage.ReglagePdvRepository reglagesPdv;
    private final tg.parcauto.api.common.LimiteurRequetes limiteur;
    private final tg.parcauto.api.common.audit.JournalActiviteService journal;

    /**
     * Message neutre : l'unicite des adresses est verifiee sur toute la plateforme,
     * le message ne doit pas reveler qu'un compte existe dans une autre entreprise.
     */
    private static final String ADRESSE_INDISPONIBLE = "Cette adresse ne peut pas etre utilisee pour un nouveau compte";

    @Transactional(readOnly = true)
    public Page<UtilisateurDto.Reponse> rechercher(String q, Role role, Boolean actif, Pageable pageable) {
        return repository.findAll(
                        UtilisateurSpecifications.rechercher(ContexteEntreprise.exiger(), q, role, actif), pageable)
                .map(this::versDto);
    }

    @Transactional(readOnly = true)
    public UtilisateurDto.Reponse parId(Long id) {
        return versDto(charger(id));
    }

    public UtilisateurDto.Reponse creer(UtilisateurDto.Creation demande) {
        abonnement.verifierLimite(tg.parcauto.api.plateforme.abonnement.Ressource.UTILISATEURS);
        if (repository.existsByEmailIgnoreCase(demande.email())) {
            throw new BusinessException(ADRESSE_INDISPONIBLE);
        }
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setEntrepriseId(ContexteEntreprise.exiger());
        utilisateur.setEmail(demande.email().trim());
        utilisateur.setNom(demande.nom().trim());
        utilisateur.setPrenom(vide(demande.prenom()) ? null : demande.prenom().trim());
        utilisateur.setTelephone(vide(demande.telephone()) ? null : demande.telephone().trim());
        utilisateur.setRoles(attribuables(copie(demande.roles()), Set.of()));
        utilisateur.setMotDePasse(encodeur.encode(demande.motDePasse()));
        // Mot de passe choisi par l'administrateur : provisoire. Le titulaire le remplace a sa
        // premiere connexion, l'administrateur ne peut plus agir en son nom.
        utilisateur.setMotDePasseAChanger(true);
        utilisateur.setActif(true);
        Utilisateur cree = repository.save(utilisateur);
        tracer(cree, "PROFILS", "profils accordes a la creation", null, cree.getRoles());
        return versDto(cree);
    }

    /** Trace une action sur un compte ; les profils avant/apres sont listes en clair. */
    private void tracer(Utilisateur u, String action, String quoi, Set<Role> avant, Set<Role> apres) {
        journal.administration(u.getEntrepriseId(), action, "Utilisateur", "Utilisateur", u.getId(),
                "Compte « " + u.nomComplet() + " » : " + quoi,
                avant == null ? null : java.util.Map.of("profils", profils(avant)),
                apres == null ? null : java.util.Map.of("profils", profils(apres)));
    }

    private static String profils(Set<Role> roles) {
        return roles.stream().map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(", "));
    }

    public UtilisateurDto.Reponse modifier(Long id, UtilisateurDto.Modification demande, Long idCourant) {
        Utilisateur utilisateur = charger(id);

        repository.findByEmailIgnoreCase(demande.email())
                .filter(autre -> !autre.getId().equals(id))
                .ifPresent(autre -> {
                    throw new BusinessException(ADRESSE_INDISPONIBLE);
                });

        Set<Role> roles = attribuables(copie(demande.roles()), utilisateur.getRoles());
        if (id.equals(idCourant) && !roles.contains(Role.ADMIN)
                && utilisateur.getRoles().contains(Role.ADMIN)) {
            throw new BusinessException(
                    "Vous ne pouvez pas retirer votre propre profil administrateur");
        }
        if (utilisateur.getRoles().contains(Role.ADMIN) && !roles.contains(Role.ADMIN)) {
            verifierDernierAdministrateur(id);
        }
        if (!roles.equals(utilisateur.getRoles())) {
            tracer(utilisateur, "PROFILS", "profils modifies", EnumSet.copyOf(utilisateur.getRoles().isEmpty()
                    ? EnumSet.noneOf(Role.class) : utilisateur.getRoles()), roles);
        }

        if (!utilisateur.getEmail().equalsIgnoreCase(demande.email().trim())) {
            // Nouvelle adresse : les jetons de l'ancienne ne doivent pas suivre quiconque la reprendrait.
            utilisateur.invaliderJetons();
        }
        utilisateur.setEmail(demande.email().trim());
        utilisateur.setNom(demande.nom().trim());
        utilisateur.setPrenom(vide(demande.prenom()) ? null : demande.prenom().trim());
        utilisateur.setTelephone(vide(demande.telephone()) ? null : demande.telephone().trim());
        utilisateur.setRoles(roles);
        return versDto(utilisateur);
    }

    /** Active ou desactive un compte ; un compte desactive ne peut plus se connecter. */
    public UtilisateurDto.Reponse changerActivation(Long id, boolean actif, Long idCourant) {
        Utilisateur utilisateur = charger(id);
        if (!actif) {
            if (id.equals(idCourant)) {
                throw new BusinessException("Vous ne pouvez pas desactiver votre propre compte");
            }
            if (utilisateur.getRoles().contains(Role.ADMIN)) {
                verifierDernierAdministrateur(id);
            }
        }
        if (actif && !utilisateur.isActif()) {
            abonnement.verifierLimite(tg.parcauto.api.plateforme.abonnement.Ressource.UTILISATEURS);
        }
        if (utilisateur.isActif() != actif) {
            tracer(utilisateur, actif ? "ACTIVATION" : "DESACTIVATION", actif ? "compte active" : "compte desactive",
                    null, null);
        }
        utilisateur.setActif(actif);
        return versDto(utilisateur);
    }

    /** Reinitialisation par un administrateur : l'ancien mot de passe n'est pas demande. */
    /** Le gerant definit le code PIN d'un compte, ou l'efface pour que le caissier en cree un. */
    public void definirPin(Long id, String pin) {
        Utilisateur u = charger(id);
        u.setPinHash(pin == null || pin.isBlank() ? null : encodeur.encode(pin));
        tracer(u, "PIN", pin == null || pin.isBlank() ? "code PIN efface" : "code PIN defini par le gerant", null, null);
    }

    public void reinitialiserMotDePasse(Long id, String motDePasse) {
        Utilisateur u = charger(id);
        u.setMotDePasse(encodeur.encode(motDePasse));
        // Provisoire, comme a la creation : l'administrateur ne garde pas la connaissance du mot de passe.
        u.setMotDePasseAChanger(true);
        // Un mot de passe vole ne garde pas sa session ouverte.
        u.invaliderJetons();
        tracer(u, "MOT_DE_PASSE", "mot de passe reinitialise par un administrateur", null, null);
    }

    /** Deconnexion : les jetons deja emis pour ce compte ne sont plus acceptes. */
    public void deconnecter(Long id) {
        repository.findById(id).ifPresent(Utilisateur::invaliderJetons);
    }

    /** Compte de la personne connectee (y compris l'administrateur de la plateforme). */
    @Transactional(readOnly = true)
    public UtilisateurDto.Reponse moi(Long id) {
        return versDto(repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", id)));
    }

    /** Nom, prenom et telephone mis a jour par l'utilisateur lui-meme. */
    public UtilisateurDto.Reponse mettreAJourSonProfil(Long id, UtilisateurDto.MiseAJourProfil demande) {
        Utilisateur utilisateur = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", id));
        utilisateur.setNom(demande.nom().trim());
        utilisateur.setPrenom(demande.prenom() == null || demande.prenom().isBlank() ? null : demande.prenom().trim());
        utilisateur.setTelephone(demande.telephone() == null || demande.telephone().isBlank() ? null : demande.telephone().trim());
        return versDto(utilisateur);
    }

    /**
     * Changement par l'utilisateur lui-meme, qui doit prouver l'ancien mot de passe.
     * Ouvert aussi a l'administrateur de la plateforme, qui n'a pas d'entreprise.
     */
    public void changerSonMotDePasse(Long id, UtilisateurDto.ChangementMotDePasse demande) {
        Utilisateur utilisateur = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", id));
        // Un jeton vole ne permet pas d'essayer l'ancien mot de passe sans limite.
        String cle = "mdp-actuel:" + id;
        limiteur.verifier(cle, 5, java.time.Duration.ofMinutes(15),
                "Trop d'essais du mot de passe actuel. Reessayez dans quelques minutes.");
        if (!encodeur.matches(demande.motDePasseActuel(), utilisateur.getMotDePasse())) {
            limiteur.noter(cle);
            throw new BusinessException("Le mot de passe actuel est incorrect");
        }
        limiteur.oublier(cle);
        if (encodeur.matches(demande.nouveauMotDePasse(), utilisateur.getMotDePasse())) {
            throw new BusinessException("Le nouveau mot de passe doit differer de l'actuel");
        }
        utilisateur.setMotDePasse(encodeur.encode(demande.nouveauMotDePasse()));
        utilisateur.setMotDePasseAChanger(false);
        // Les autres sessions se ferment ; celle-ci recoit un nouveau jeton.
        utilisateur.invaliderJetons();
    }

    /**
     * Profils attribuables dans l'entreprise : ceux des modules qu'elle a
     * actives. Serveur et cuisinier n'ont de sens que pour un point de vente en
     * mode restaurant. Le profil de la plateforme n'en fait jamais partie.
     */
    @Transactional(readOnly = true)
    public List<UtilisateurDto.ProfilRole> profils() {
        Long entreprise = ContexteEntreprise.exiger();
        Set<ModuleApplicatif> actifs = moduleService.actifs(entreprise);
        boolean restauration = "RESTAURATION".equals(moduleService.variante(entreprise, ModuleApplicatif.POINT_DE_VENTE));
        Set<Role> restaurant = EnumSet.of(Role.SERVEUR, Role.PREPARATEUR);
        return java.util.Arrays.stream(Role.values())
                .filter(role -> role != Role.SUPER_ADMIN)
                .filter(role -> role == Role.ADMIN || actifs.stream().anyMatch(m -> m.getProfils().contains(role)))
                .filter(role -> restauration || !restaurant.contains(role))
                .filter(role -> role != Role.MAGASINIER
                        || "MATERIAUX".equals(moduleService.variante(entreprise, ModuleApplicatif.POINT_DE_VENTE)))
                // Associe : seulement quand l'option des benefices est activee dans les reglages du point de vente.
                .filter(role -> role != Role.ASSOCIE || associesActifs())
                .map(role -> new UtilisateurDto.ProfilRole(role.name(), role.getLibelle()))
                .toList();
    }

    private boolean associesActifs() {
        return reglagesPdv.findFirstByOrderByIdAsc()
                .map(tg.parcauto.api.pointdevente.reglage.ReglagePdv::isAssociesActif).orElse(false);
    }

    private void verifierDernierAdministrateur(Long id) {
        // Verrou par entreprise : deux administrateurs qui se retirent mutuellement le profil en
        // meme temps ne laissent pas l'entreprise sans administrateur.
        repository.verrouillerAdministrateurs(ContexteEntreprise.exiger());
        if (repository.compterAdministrateursActifsHors(id, ContexteEntreprise.exiger()) == 0) {
            throw new BusinessException(
                    "Ce compte est le dernier administrateur actif : l'entreprise ne peut pas rester sans administrateur");
        }
    }

    private Utilisateur charger(Long id) {
        return repository.findByIdAndEntrepriseId(id, ContexteEntreprise.exiger())
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", id));
    }

    /** Un administrateur d'entreprise ne peut pas s'octroyer, ni octroyer, le profil de la plateforme. */
    /**
     * Refuse un profil que l'entreprise ne propose pas (module non active, serveur
     * hors restaurant...). Un profil que le compte avait deja peut rester.
     */
    private Set<Role> attribuables(Set<Role> roles, Set<Role> dejaAttribues) {
        Set<String> proposes = profils().stream().map(UtilisateurDto.ProfilRole::valeur)
                .collect(java.util.stream.Collectors.toSet());
        roles.stream()
                .filter(r -> !proposes.contains(r.name()) && !dejaAttribues.contains(r))
                .findFirst()
                .ifPresent(r -> {
                    throw new BusinessException("Le profil " + r.getLibelle()
                            + " n'est pas disponible : le module correspondant n'est pas active");
                });
        if (roles.isEmpty()) {
            throw new BusinessException("Choisissez au moins un profil");
        }
        return roles;
    }

    private Set<Role> copie(Set<Role> roles) {
        if (roles.contains(Role.SUPER_ADMIN)) {
            throw new BusinessException("Le profil administrateur plateforme ne peut pas etre attribue ici");
        }
        return roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
    }

    private boolean vide(String valeur) {
        return valeur == null || valeur.isBlank();
    }

    private UtilisateurDto.Reponse versDto(Utilisateur u) {
        return new UtilisateurDto.Reponse(
                u.getId(), u.getEmail(), u.getNom(), u.getPrenom(), u.nomComplet(),
                u.getTelephone(), u.isActif(), u.getRoles(),
                u.getRoles().stream().map(Role::getLibelle).sorted().toList(),
                u.getCreeLe(), u.getModifieLe(), u.getPinHash() != null);
    }
}
