package tg.parcauto.api.agence.catalogue;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.agence.AuditAgenceService;
import tg.parcauto.api.agence.InitialisationAgence;
import tg.parcauto.api.agence.tresorerie.CompteService;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Catalogue des services, reseaux et types d'operation, modifiable par
 * l'administrateur sans developpement. Les operations proposees a la saisie
 * dependent du service choisi ; la regle de mouvement de chaque type dit dans
 * quel sens bougent le cash et le virtuel.
 * <p>
 * Rien ne se supprime : un element desactive disparait de la saisie mais garde
 * son historique.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CatalogueAgenceService {

    private final ServiceFinancierRepository services;
    private final ReseauRepository reseaux;
    private final TypeOperationRepository types;
    private final InitialisationAgence initialisation;
    private final CompteService comptes;
    private final AuditAgenceService audit;

    public record RequeteService(@NotBlank @Size(max = 120) String nom, @NotBlank @Size(max = 60) String categorie,
                                 Integer ordre, Boolean actif) {
    }

    public record RequeteReseau(@NotNull Long serviceId, @NotBlank @Size(max = 120) String nom,
                                Integer ordre, Boolean actif) {
    }

    public record RequeteType(@NotNull Long serviceId, @NotBlank @Size(max = 120) String libelle,
                              @NotNull @Min(-1) @Max(1) Integer effetCash,
                              @NotNull @Min(-1) @Max(1) Integer effetVirtuel,
                              @NotNull TypeOperation.CompteVirtuel compteVirtuel,
                              TypeOperation.Nature nature, Integer ordre, Boolean actif) {
    }

    public record ReseauDto(Long id, String code, String nom, int ordre, boolean actif) {
    }

    public record TypeDto(Long id, String code, String libelle, int effetCash, int effetVirtuel,
                          TypeOperation.CompteVirtuel compteVirtuel, TypeOperation.Nature nature,
                          int ordre, boolean actif, String regle) {
    }

    public record ServiceDto(Long id, String code, String nom, String categorie, int ordre, boolean actif,
                             List<ReseauDto> reseaux, List<TypeDto> types) {
    }

    /** Catalogue complet, regroupe par service ; le catalogue par defaut est pose a la premiere lecture. */
    public List<ServiceDto> catalogue() {
        initialisation.assurer();
        Map<Long, List<Reseau>> reseauxParService = reseaux.findAllByOrderByOrdreAscNomAsc().stream()
                .collect(Collectors.groupingBy(r -> r.getService().getId()));
        Map<Long, List<TypeOperation>> typesParService = types.findAllByOrderByOrdreAscLibelleAsc().stream()
                .collect(Collectors.groupingBy(t -> t.getService().getId()));
        return services.findAllByOrderByOrdreAscNomAsc().stream()
                .map(s -> new ServiceDto(s.getId(), s.getCode(), s.getNom(), s.getCategorie(), s.getOrdre(), s.isActif(),
                        reseauxParService.getOrDefault(s.getId(), List.of()).stream().map(CatalogueAgenceService::versDto).toList(),
                        typesParService.getOrDefault(s.getId(), List.of()).stream().map(t -> versDto(t, s)).toList()))
                .toList();
    }

    /** Categories deja employees, pour la saisie. */
    @Transactional(readOnly = true)
    public List<String> categories() {
        return services.findAll().stream().map(ServiceFinancier::getCategorie).distinct()
                .sorted(Comparator.comparing(c -> c.toLowerCase(Locale.ROOT))).toList();
    }

    // ------------------------------------------------------------- services

    public ServiceDto creerService(RequeteService r) {
        initialisation.assurer();
        String nom = r.nom().trim();
        if (services.existsByNomIgnoreCase(nom)) {
            throw new BusinessException("Un service porte deja le nom " + nom);
        }
        ServiceFinancier s = new ServiceFinancier();
        s.setCode(codeLibre(nom, services::existsByCodeIgnoreCase));
        appliquer(s, r);
        if (r.ordre() == null) {
            s.setOrdre(services.findAll().stream().mapToInt(ServiceFinancier::getOrdre).max().orElse(0) + 10);
        }
        s = services.save(s);
        audit.tracer(null, "CREATION", "SERVICE", s.getId(), "Service " + s.getNom() + " cree", null, r);
        return trouver(s.getId());
    }

    public ServiceDto modifierService(Long id, RequeteService r) {
        ServiceFinancier s = services.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Service", id));
        String nom = r.nom().trim();
        if (services.existsByNomIgnoreCaseAndIdNot(nom, id)) {
            throw new BusinessException("Un service porte deja le nom " + nom);
        }
        ServiceDto avant = trouver(id);
        appliquer(s, r);
        services.flush();
        comptes.synchroniserTout();
        ServiceDto apres = trouver(id);
        audit.tracer(null, "MODIFICATION", "SERVICE", id, "Service " + s.getNom() + " modifie", sansDetail(avant),
                sansDetail(apres));
        return apres;
    }

    private void appliquer(ServiceFinancier s, RequeteService r) {
        s.setNom(r.nom().trim());
        s.setCategorie(r.categorie().trim());
        if (r.ordre() != null) {
            s.setOrdre(r.ordre());
        }
        if (r.actif() != null) {
            s.setActif(r.actif());
        }
    }

    // -------------------------------------------------------------- reseaux

    public ServiceDto creerReseau(RequeteReseau r) {
        ServiceFinancier s = services.findById(r.serviceId())
                .orElseThrow(() -> ResourceNotFoundException.of("Service", r.serviceId()));
        String nom = r.nom().trim();
        if (reseaux.findAllByOrderByOrdreAscNomAsc().stream()
                .anyMatch(x -> x.getService().getId().equals(s.getId()) && x.getNom().equalsIgnoreCase(nom))) {
            throw new BusinessException("Le reseau " + nom + " existe deja pour " + s.getNom());
        }
        Reseau reseau = new Reseau();
        reseau.setService(s);
        reseau.setCode(codeLibre(nom, code -> reseaux.existsByServiceIdAndCodeIgnoreCase(s.getId(), code)));
        reseau.setNom(nom);
        reseau.setOrdre(r.ordre() != null ? r.ordre() : (reseaux.findAllByOrderByOrdreAscNomAsc().stream()
                .filter(x -> x.getService().getId().equals(s.getId())).mapToInt(Reseau::getOrdre).max().orElse(0) + 10));
        if (r.actif() != null) {
            reseau.setActif(r.actif());
        }
        reseau = reseaux.save(reseau);
        audit.tracer(null, "CREATION", "RESEAU", reseau.getId(), "Reseau " + nom + " ajoute a " + s.getNom(), null, r);
        return trouver(s.getId());
    }

    public ServiceDto modifierReseau(Long id, RequeteReseau r) {
        Reseau reseau = reseaux.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Reseau", id));
        ReseauDto avant = versDto(reseau);
        String nom = r.nom().trim();
        boolean doublon = reseaux.findAllByOrderByOrdreAscNomAsc().stream()
                .anyMatch(x -> !x.getId().equals(id) && x.getService().getId().equals(reseau.getService().getId())
                        && x.getNom().equalsIgnoreCase(nom));
        if (doublon) {
            throw new BusinessException("Le reseau " + nom + " existe deja pour " + reseau.getService().getNom());
        }
        reseau.setNom(nom);
        if (r.ordre() != null) {
            reseau.setOrdre(r.ordre());
        }
        if (r.actif() != null) {
            reseau.setActif(r.actif());
        }
        audit.tracer(null, "MODIFICATION", "RESEAU", id, "Reseau " + nom + " modifie", avant, versDto(reseau));
        return trouver(reseau.getService().getId());
    }

    // ------------------------------------------------------ types d'operation

    public ServiceDto creerType(RequeteType r) {
        ServiceFinancier s = services.findById(r.serviceId())
                .orElseThrow(() -> ResourceNotFoundException.of("Service", r.serviceId()));
        String libelle = r.libelle().trim();
        verifierLibelleType(s.getId(), libelle, null);
        TypeOperation type = new TypeOperation();
        type.setService(s);
        type.setCode(codeLibre(libelle, code -> types.existsByServiceIdAndCodeIgnoreCase(s.getId(), code)));
        appliquer(type, r);
        if (r.ordre() == null) {
            type.setOrdre(types.findAllByOrderByOrdreAscLibelleAsc().stream()
                    .filter(x -> x.getService().getId().equals(s.getId())).mapToInt(TypeOperation::getOrdre).max().orElse(0) + 10);
        }
        type = types.save(type);
        types.flush();
        comptes.synchroniserTout();
        audit.tracer(null, "CREATION", "TYPE_OPERATION", type.getId(),
                "Operation " + libelle + " ajoutee a " + s.getNom() + " : " + regle(type), null, r);
        return trouver(s.getId());
    }

    public ServiceDto modifierType(Long id, RequeteType r) {
        TypeOperation type = types.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Type d'operation", id));
        ServiceFinancier s = type.getService();
        TypeDto avant = versDto(type, s);
        verifierLibelleType(s.getId(), r.libelle().trim(), id);
        appliquer(type, r);
        types.flush();
        comptes.synchroniserTout();
        TypeDto apres = versDto(type, s);
        audit.tracer(null, "MODIFICATION", "TYPE_OPERATION", id,
                "Operation " + type.getLibelle() + " (" + s.getNom() + ") modifiee : " + regle(type), avant, apres);
        return trouver(s.getId());
    }

    private void verifierLibelleType(Long serviceId, String libelle, Long sauf) {
        boolean doublon = types.findAllByOrderByOrdreAscLibelleAsc().stream()
                .anyMatch(x -> x.getService().getId().equals(serviceId) && !x.getId().equals(sauf)
                        && x.getLibelle().equalsIgnoreCase(libelle));
        if (doublon) {
            throw new BusinessException("L'operation " + libelle + " existe deja pour ce service");
        }
    }

    private void appliquer(TypeOperation t, RequeteType r) {
        TypeOperation.CompteVirtuel compte = r.compteVirtuel();
        int virtuel = r.effetVirtuel();
        if ((compte == TypeOperation.CompteVirtuel.AUCUN) != (virtuel == 0)) {
            throw new BusinessException(compte == TypeOperation.CompteVirtuel.AUCUN
                    ? "Sans compte electronique, le virtuel ne peut pas bouger"
                    : "Choisissez si le compte electronique augmente ou diminue");
        }
        if (r.effetCash() == 0 && virtuel == 0) {
            throw new BusinessException("L'operation doit faire bouger au moins le cash ou le virtuel");
        }
        t.setLibelle(r.libelle().trim());
        t.setEffetCash(r.effetCash());
        t.setEffetVirtuel(virtuel);
        t.setCompteVirtuel(compte);
        t.setNature(r.nature() != null ? r.nature() : TypeOperation.Nature.TRANSIT);
        if (r.ordre() != null) {
            t.setOrdre(r.ordre());
        }
        if (r.actif() != null) {
            t.setActif(r.actif());
        }
    }

    // ---------------------------------------------------------------- outils

    private ServiceDto trouver(Long serviceId) {
        return catalogue().stream().filter(s -> s.id().equals(serviceId)).findFirst()
                .orElseThrow(() -> ResourceNotFoundException.of("Service", serviceId));
    }

    private static ServiceDto sansDetail(ServiceDto s) {
        return new ServiceDto(s.id(), s.code(), s.nom(), s.categorie(), s.ordre(), s.actif(), null, null);
    }

    private static ReseauDto versDto(Reseau r) {
        return new ReseauDto(r.getId(), r.getCode(), r.getNom(), r.getOrdre(), r.isActif());
    }

    private static TypeDto versDto(TypeOperation t, ServiceFinancier s) {
        return new TypeDto(t.getId(), t.getCode(), t.getLibelle(), t.getEffetCash(), t.getEffetVirtuel(),
                t.getCompteVirtuel(), t.getNature(), t.getOrdre(), t.isActif(), regle(t));
    }

    /** Regle lisible : « Cash + montant, Float - montant ». */
    static String regle(TypeOperation t) {
        StringBuilder sb = new StringBuilder("Cash ").append(sens(t.getEffetCash()));
        if (t.getCompteVirtuel() != TypeOperation.CompteVirtuel.AUCUN) {
            sb.append(", ").append(t.getCompteVirtuel() == TypeOperation.CompteVirtuel.FLOAT ? "float" : "recharge")
                    .append(' ').append(sens(t.getEffetVirtuel()));
        }
        return sb.toString();
    }

    private static String sens(int effet) {
        return effet > 0 ? "+ montant" : effet < 0 ? "- montant" : "inchange";
    }

    /** Code stable derive du nom : « Western Union » donne WESTERN_UNION, puis WESTERN_UNION_2... */
    private static String codeLibre(String nom, java.util.function.Predicate<String> pris) {
        String base = Normalizer.normalize(nom, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        if (base.isEmpty()) {
            base = "ELEMENT";
        }
        if (base.length() > 34) {
            base = base.substring(0, 34);
        }
        String code = base;
        for (int i = 2; pris.test(code); i++) {
            code = base + "_" + i;
        }
        return code;
    }
}
