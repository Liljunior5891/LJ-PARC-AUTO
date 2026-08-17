package tg.parcauto.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Gestion des comptes utilisateurs (chapitre 40).
 * <p>
 * Deux garde-fous structurent ce service : on ne se retire jamais soi-meme des
 * moyens de se reconnecter, et le parc ne peut pas se retrouver sans
 * administrateur actif.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UtilisateurService {

    private final UtilisateurRepository repository;
    private final PasswordEncoder encodeur;

    @Transactional(readOnly = true)
    public Page<UtilisateurDto.Reponse> rechercher(String q, Role role, Boolean actif, Pageable pageable) {
        return repository.findAll(UtilisateurSpecifications.rechercher(q, role, actif), pageable)
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
    public void reinitialiserMotDePasse(Long id, String motDePasse) {
        charger(id).setMotDePasse(encodeur.encode(motDePasse));
    }

    /** Changement par l'utilisateur lui-meme, qui doit prouver l'ancien mot de passe. */
    public void changerSonMotDePasse(Long id, UtilisateurDto.ChangementMotDePasse demande) {
        Utilisateur utilisateur = charger(id);
        if (!encodeur.matches(demande.motDePasseActuel(), utilisateur.getMotDePasse())) {
            throw new BusinessException("Le mot de passe actuel est incorrect");
        }
        if (encodeur.matches(demande.nouveauMotDePasse(), utilisateur.getMotDePasse())) {
            throw new BusinessException("Le nouveau mot de passe doit differer de l'actuel");
        }
        utilisateur.setMotDePasse(encodeur.encode(demande.nouveauMotDePasse()));
    }

    @Transactional(readOnly = true)
    public List<UtilisateurDto.ProfilRole> profils() {
        return java.util.Arrays.stream(Role.values())
                .map(role -> new UtilisateurDto.ProfilRole(role.name(), role.getLibelle()))
                .toList();
    }

    private void verifierDernierAdministrateur(Long id) {
        if (repository.compterAdministrateursActifsHors(id) == 0) {
            throw new BusinessException(
                    "Ce compte est le dernier administrateur actif : le parc ne peut pas rester sans administrateur");
        }
    }

    private Utilisateur charger(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", id));
    }

    private Set<Role> copie(Set<Role> roles) {
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
                u.getCreeLe(), u.getModifieLe());
    }
}
