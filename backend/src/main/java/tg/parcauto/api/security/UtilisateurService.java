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

    private final UtilisateurRepository repository;
    private final PasswordEncoder encodeur;
    private final ModuleService moduleService;

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
        if (repository.existsByEmailIgnoreCase(demande.email())) {
            throw new BusinessException("Un compte existe deja avec l'adresse " + demande.email());
        }
        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setEntrepriseId(ContexteEntreprise.exiger());
        utilisateur.setEmail(demande.email().trim());
        utilisateur.setNom(demande.nom().trim());
        utilisateur.setPrenom(vide(demande.prenom()) ? null : demande.prenom().trim());
        utilisateur.setTelephone(vide(demande.telephone()) ? null : demande.telephone().trim());
        utilisateur.setRoles(copie(demande.roles()));
        utilisateur.setMotDePasse(encodeur.encode(demande.motDePasse()));
        utilisateur.setActif(true);
        return versDto(repository.save(utilisateur));
    }

    public UtilisateurDto.Reponse modifier(Long id, UtilisateurDto.Modification demande, Long idCourant) {
        Utilisateur utilisateur = charger(id);

        repository.findByEmailIgnoreCase(demande.email())
                .filter(autre -> !autre.getId().equals(id))
                .ifPresent(autre -> {
                    throw new BusinessException("Un compte existe deja avec l'adresse " + demande.email());
                });

        Set<Role> roles = copie(demande.roles());
        if (id.equals(idCourant) && !roles.contains(Role.ADMIN)
                && utilisateur.getRoles().contains(Role.ADMIN)) {
            throw new BusinessException(
                    "Vous ne pouvez pas retirer votre propre profil administrateur");
        }
        if (utilisateur.getRoles().contains(Role.ADMIN) && !roles.contains(Role.ADMIN)) {
            verifierDernierAdministrateur(id);
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
        utilisateur.setActif(actif);
        return versDto(utilisateur);
    }

    /** Reinitialisation par un administrateur : l'ancien mot de passe n'est pas demande. */
    /** Le gerant definit le code PIN d'un compte, ou l'efface pour que le caissier en cree un. */
    public void definirPin(Long id, String pin) {
        charger(id).setPinHash(pin == null || pin.isBlank() ? null : encodeur.encode(pin));
    }

    public void reinitialiserMotDePasse(Long id, String motDePasse) {
        charger(id).setMotDePasse(encodeur.encode(motDePasse));
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
        if (!encodeur.matches(demande.motDePasseActuel(), utilisateur.getMotDePasse())) {
            throw new BusinessException("Le mot de passe actuel est incorrect");
        }
        if (encodeur.matches(demande.nouveauMotDePasse(), utilisateur.getMotDePasse())) {
            throw new BusinessException("Le nouveau mot de passe doit differer de l'actuel");
        }
        utilisateur.setMotDePasse(encodeur.encode(demande.nouveauMotDePasse()));
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
                .map(role -> new UtilisateurDto.ProfilRole(role.name(), role.getLibelle()))
                .toList();
    }

    private void verifierDernierAdministrateur(Long id) {
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
