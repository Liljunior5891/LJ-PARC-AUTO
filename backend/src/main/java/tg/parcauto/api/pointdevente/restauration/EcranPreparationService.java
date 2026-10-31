package tg.parcauto.api.pointdevente.restauration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.pointdevente.catalogue.Categorie;
import tg.parcauto.api.pointdevente.catalogue.CategorieRepository;
import tg.parcauto.api.pointdevente.catalogue.PostePreparation;
import tg.parcauto.api.pointdevente.personnel.PersonnelService;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Ecrans de preparation : categories recues et personnel affecte. */
@Service
@RequiredArgsConstructor
@Transactional
public class EcranPreparationService {

    private final EcranPreparationRepository repository;
    private final CategorieRepository categorieRepository;
    private final PersonnelService personnel;

    public record Requete(
            @NotBlank @Size(max = 80) String nom,
            Boolean actif,
            Set<Long> categorieIds,
            Set<Long> utilisateurIds) {
    }

    public record CategorieEcran(Long id, String nom, String couleur, String icone) {

        static CategorieEcran de(Categorie c) {
            return new CategorieEcran(c.getId(), c.getNom(), c.getCouleur(), c.getIcone());
        }
    }

    public record Reponse(Long id, String nom, boolean actif, List<CategorieEcran> categories,
                          List<PersonnelService.Membre> utilisateurs) {
    }

    /** Ecrans de l'etablissement, et les categories preparees qu'aucun ecran ne recoit. */
    public record Liste(List<Reponse> ecrans, List<CategorieEcran> categoriesSansEcran) {
    }

    @Transactional(readOnly = true)
    public Liste lister() {
        List<EcranPreparation> ecrans = repository.findAllByOrderByNomAsc();
        Map<Long, PersonnelService.Membre> membres = personnel.parId(ecrans.stream()
                .flatMap(e -> e.getUtilisateurIds().stream()).collect(Collectors.toSet()));
        Set<Long> couvertes = ecrans.stream().filter(EcranPreparation::isActif)
                .flatMap(e -> e.getCategories().stream()).map(Categorie::getId).collect(Collectors.toSet());
        List<CategorieEcran> orphelines = categorieRepository.findAllByOrderByOrdreAscNomAsc().stream()
                .filter(c -> c.isActif() && c.getPoste() != PostePreparation.AUCUN && !couvertes.contains(c.getId()))
                .map(CategorieEcran::de)
                .toList();
        return new Liste(ecrans.stream().map(e -> versDto(e, membres)).toList(), orphelines);
    }

    /** Ecrans que l'utilisateur peut ouvrir : les siens, ou tous pour un gerant. */
    @Transactional(readOnly = true)
    public List<Reponse> accessibles(UtilisateurPrincipal utilisateur) {
        boolean gestionnaire = PersonnelService.gestionnaire(utilisateur);
        return repository.findAllByOrderByNomAsc().stream()
                .filter(EcranPreparation::isActif)
                .filter(e -> gestionnaire || e.getUtilisateurIds().contains(utilisateur.getId()))
                .map(e -> versDto(e, Map.of()))
                .toList();
    }

    /** Categories que recoit un ecran ouvert par l'utilisateur ; refus s'il ne lui est pas affecte. */
    @Transactional(readOnly = true)
    public Set<Long> categoriesPour(Long ecranId, UtilisateurPrincipal utilisateur) {
        EcranPreparation ecran = charger(ecranId);
        if (!ecran.isActif()) {
            throw new BusinessException("L'ecran " + ecran.getNom() + " est desactive");
        }
        if (!PersonnelService.gestionnaire(utilisateur) && !ecran.getUtilisateurIds().contains(utilisateur.getId())) {
            throw new BusinessException("L'ecran " + ecran.getNom() + " ne vous est pas affecte");
        }
        return ecran.getCategories().stream().map(Categorie::getId).collect(Collectors.toSet());
    }

    public Reponse creer(Requete r) {
        if (repository.existsByNomIgnoreCase(r.nom().trim())) {
            throw new BusinessException("Un ecran porte deja le nom " + r.nom().trim());
        }
        EcranPreparation ecran = new EcranPreparation();
        appliquer(ecran, r);
        ecran = repository.saveAndFlush(ecran);
        return versDto(ecran, personnel.parId(ecran.getUtilisateurIds()));
    }

    public Reponse modifier(Long id, Requete r) {
        EcranPreparation ecran = charger(id);
        if (repository.existsByNomIgnoreCaseAndIdNot(r.nom().trim(), id)) {
            throw new BusinessException("Un ecran porte deja le nom " + r.nom().trim());
        }
        appliquer(ecran, r);
        repository.flush();
        return versDto(ecran, personnel.parId(ecran.getUtilisateurIds()));
    }

    private void appliquer(EcranPreparation e, Requete r) {
        e.setNom(r.nom().trim());
        if (r.actif() != null) {
            e.setActif(r.actif());
        }
        if (r.categorieIds() != null) {
            List<Categorie> categories = categorieRepository.findAllById(r.categorieIds());
            if (categories.size() != new HashSet<>(r.categorieIds()).size()) {
                throw new BusinessException("Une des categories choisies n'existe pas");
            }
            e.getCategories().clear();
            e.getCategories().addAll(categories);
        }
        if (r.utilisateurIds() != null) {
            e.getUtilisateurIds().clear();
            e.getUtilisateurIds().addAll(personnel.verifier(r.utilisateurIds()));
        }
    }

    private Reponse versDto(EcranPreparation e, Map<Long, PersonnelService.Membre> membres) {
        return new Reponse(e.getId(), e.getNom(), e.isActif(),
                e.getCategories().stream()
                        .sorted(Comparator.comparing(Categorie::getOrdre).thenComparing(Categorie::getNom))
                        .map(CategorieEcran::de).toList(),
                e.getUtilisateurIds().stream().map(membres::get).filter(java.util.Objects::nonNull)
                        .sorted(Comparator.comparing(PersonnelService.Membre::nomComplet)).toList());
    }

    private EcranPreparation charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Ecran de preparation", id));
    }
}
