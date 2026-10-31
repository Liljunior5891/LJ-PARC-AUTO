package tg.parcauto.api.pointdevente.personnel;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.Utilisateur;
import tg.parcauto.api.security.UtilisateurPrincipal;
import tg.parcauto.api.security.UtilisateurRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Personnel du point de vente, pour affecter caisses et ecrans de preparation.
 * Les comptes ne sont pas cloisonnes par Hibernate : chaque lecture nomme
 * l'entreprise courante.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonnelService {

    private final UtilisateurRepository repository;

    public record Membre(Long id, String nomComplet, List<String> roles, boolean actif) {

        public static Membre de(Utilisateur u) {
            return new Membre(u.getId(), u.nomComplet(),
                    u.getRoles().stream().map(Role::getLibelle).sorted().toList(), u.isActif());
        }
    }

    /** Comptes de l'entreprise qui travaillent au point de vente. */
    public List<Membre> personnel() {
        Set<Role> profils = ModuleApplicatif.POINT_DE_VENTE.getProfils();
        return repository.findByEntrepriseIdOrderByNomAscPrenomAsc(ContexteEntreprise.exiger()).stream()
                .filter(u -> u.getRoles().stream().anyMatch(profils::contains))
                .map(Membre::de)
                .toList();
    }

    /** Noms des comptes, pour l'affichage des affectations. */
    public Map<Long, Membre> parId(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Long entreprise = ContexteEntreprise.exiger();
        return repository.findAllById(ids).stream()
                .filter(u -> entreprise.equals(u.getEntrepriseId()))
                .collect(Collectors.toMap(Utilisateur::getId, Membre::de, (a, b) -> a));
    }

    /** Verifie que les comptes affectes appartiennent bien a l'entreprise. */
    public Set<Long> verifier(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new java.util.HashSet<>();
        }
        Map<Long, Membre> trouves = parId(ids);
        if (trouves.size() != new java.util.HashSet<>(ids).size()) {
            throw new BusinessException("Un des comptes choisis n'appartient pas a votre entreprise");
        }
        return new java.util.HashSet<>(ids);
    }

    public static boolean gestionnaire(UtilisateurPrincipal p) {
        return p.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals(Role.ADMIN.authority()) || a.getAuthority().equals(Role.GERANT.authority()));
    }

    @SuppressWarnings("unused")
    private static <T> Function<T, T> identite() {
        return Function.identity();
    }
}
