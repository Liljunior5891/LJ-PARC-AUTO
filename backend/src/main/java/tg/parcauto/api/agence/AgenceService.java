package tg.parcauto.api.agence;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.agence.tresorerie.CompteService;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;
import tg.parcauto.api.pointdevente.personnel.PersonnelService;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.UtilisateurRepository;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agences de l'entreprise : chacune a sa gestion (caisse, floats, personnel),
 * et l'administrateur les pilote toutes depuis un seul compte.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AgenceService {

    private static final Set<Role> PROFILS_AGENCE = EnumSet.of(Role.SUPERVISEUR_AGENCE, Role.CAISSIER_AGENCE);

    private final AgenceRepository repository;
    private final PerimetreAgence perimetre;
    private final PersonnelService personnel;
    private final UtilisateurRepository utilisateurs;
    private final CompteService comptes;
    private final InitialisationAgence initialisation;
    private final AuditAgenceService audit;

    public record Requete(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 120) String nom,
            @Size(max = 80) String ville,
            @Size(max = 80) String quartier,
            @Size(max = 255) String adresse,
            @Size(max = 30) String telephone,
            Long responsableId,
            Boolean actif,
            Set<Long> utilisateurIds) {
    }

    public record Reponse(Long id, String code, String nom, String ville, String quartier, String adresse,
                          String telephone, Long responsableId, String responsableNom, boolean actif,
                          List<PersonnelService.Membre> utilisateurs) {
    }

    @Transactional(readOnly = true)
    public List<Reponse> lister() {
        List<Agence> agences = perimetre.visibles();
        Map<Long, PersonnelService.Membre> membres = personnel.parId(agences.stream()
                .flatMap(a -> a.getUtilisateurIds().stream()).collect(Collectors.toSet()));
        return agences.stream().map(a -> versDto(a, membres)).toList();
    }

    @Transactional(readOnly = true)
    public Reponse detail(Long id) {
        Agence agence = perimetre.exiger(id);
        return versDto(agence, personnel.parId(agence.getUtilisateurIds()));
    }

    /** Comptes de l'entreprise qui peuvent travailler en agence (superviseurs et caissiers). */
    @Transactional(readOnly = true)
    public List<PersonnelService.Membre> personnelAgence() {
        return utilisateurs.findByEntrepriseIdOrderByNomAscPrenomAsc(ContexteEntreprise.exiger()).stream()
                .filter(u -> u.getRoles().stream().anyMatch(PROFILS_AGENCE::contains))
                .map(PersonnelService.Membre::de)
                .toList();
    }

    public Reponse creer(Requete r) {
        initialisation.assurer();
        Agence agence = new Agence();
        verifierUnicite(r, null);
        appliquer(agence, r);
        agence = repository.saveAndFlush(agence);
        comptes.synchroniser(agence);
        audit.tracer(agence.getId(), "CREATION", "AGENCE", agence.getId(), "Agence " + agence.getNom() + " creee",
                null, r);
        return detail(agence.getId());
    }

    public Reponse modifier(Long id, Requete r) {
        Agence agence = repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Agence", id));
        verifierUnicite(r, id);
        Reponse avant = versDto(agence, Map.of());
        appliquer(agence, r);
        repository.flush();
        comptes.synchroniser(agence);
        audit.tracer(id, "MODIFICATION", "AGENCE", id, "Agence " + agence.getNom() + " modifiee", avant,
                versDto(agence, Map.of()));
        return detail(id);
    }

    private void verifierUnicite(Requete r, Long id) {
        String code = r.code().trim().toUpperCase(Locale.ROOT);
        String nom = r.nom().trim();
        if (id == null ? repository.existsByCodeIgnoreCase(code) : repository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException("Une agence porte deja le code " + code);
        }
        if (id == null ? repository.existsByNomIgnoreCase(nom) : repository.existsByNomIgnoreCaseAndIdNot(nom, id)) {
            throw new BusinessException("Une agence porte deja le nom " + nom);
        }
    }

    private void appliquer(Agence a, Requete r) {
        a.setCode(r.code().trim().toUpperCase(Locale.ROOT));
        a.setNom(r.nom().trim());
        a.setVille(vide(r.ville()));
        a.setQuartier(vide(r.quartier()));
        a.setAdresse(vide(r.adresse()));
        a.setTelephone(vide(r.telephone()));
        if (r.actif() != null) {
            a.setActif(r.actif());
        }
        Set<Long> ids = personnel.verifier(r.utilisateurIds(), PROFILS_AGENCE, "Superviseur ou Caissier d'agence");
        if (r.responsableId() != null) {
            personnel.verifier(Set.of(r.responsableId()), EnumSet.of(Role.SUPERVISEUR_AGENCE), "Superviseur d'agence");
            ids = new HashSet<>(ids);
            ids.add(r.responsableId());
        }
        a.setResponsableId(r.responsableId());
        a.getUtilisateurIds().clear();
        a.getUtilisateurIds().addAll(ids);
    }

    private Reponse versDto(Agence a, Map<Long, PersonnelService.Membre> membres) {
        PersonnelService.Membre responsable = a.getResponsableId() == null ? null
                : membres.getOrDefault(a.getResponsableId(), personnel.parId(Set.of(a.getResponsableId())).get(a.getResponsableId()));
        return new Reponse(a.getId(), a.getCode(), a.getNom(), a.getVille(), a.getQuartier(), a.getAdresse(),
                a.getTelephone(), a.getResponsableId(), responsable == null ? null : responsable.nomComplet(), a.isActif(),
                a.getUtilisateurIds().stream().map(membres::get).filter(java.util.Objects::nonNull)
                        .sorted(java.util.Comparator.comparing(PersonnelService.Membre::nomComplet)).toList());
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
