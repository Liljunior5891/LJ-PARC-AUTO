package tg.parcauto.api.plateforme.maintenance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;

import java.time.Instant;
import java.util.List;

/** Notes de version publiees par l'exploitant : nouveautes et corrections de chaque mise a jour. */
@Service
@RequiredArgsConstructor
public class VersionPlateformeService {

    private final VersionPlateformeRepository repository;

    public record VersionVue(Long id, String numero, String titre, String notes, boolean rechargement,
                             Instant publieeLe, String creePar) {
        static VersionVue de(VersionPlateforme v) {
            return new VersionVue(v.getId(), v.getNumero(), v.getTitre(), v.getNotes(), v.isRechargement(),
                    v.getPublieeLe(), v.getCreePar());
        }
    }

    /** Sans {@code publieeLe}, la version est publiee tout de suite. */
    public record Saisie(@NotBlank @Size(max = 30) @Pattern(regexp = "[0-9A-Za-z][0-9A-Za-z.\\-]*",
                                 message = "Numero de version : chiffres, lettres, points et tirets (ex. 2.4.0)") String numero,
                         @NotBlank @Size(max = 150) String titre,
                         @NotBlank @Size(max = 20000) String notes,
                         Boolean rechargement,
                         Instant publieeLe) {
    }

    /** Version en vigueur : la derniere publiee. */
    @Transactional(readOnly = true)
    public VersionVue courante() {
        return repository.findByPublieeLeLessThanEqualOrderByPublieeLeDescIdDesc(Instant.now(), PageRequest.of(0, 1))
                .stream().findFirst().map(VersionVue::de).orElse(null);
    }

    /** Dernieres versions publiees, pour la page des nouveautes. */
    @Transactional(readOnly = true)
    public List<VersionVue> publiees(int limite) {
        return repository.findByPublieeLeLessThanEqualOrderByPublieeLeDescIdDesc(Instant.now(),
                PageRequest.of(0, Math.max(1, Math.min(limite, 50)))).stream().map(VersionVue::de).toList();
    }

    /** Console : toutes les versions, programmees comprises. */
    @Transactional(readOnly = true)
    public List<VersionVue> toutes() {
        return repository.findAllByOrderByPublieeLeDescIdDesc().stream().map(VersionVue::de).toList();
    }

    @Transactional
    public VersionVue publier(Saisie s) {
        if (repository.existsByNumeroIgnoreCase(s.numero().trim())) {
            throw new BusinessException("La version " + s.numero().trim() + " existe deja");
        }
        VersionPlateforme v = new VersionPlateforme();
        appliquer(v, s);
        return VersionVue.de(repository.save(v));
    }

    @Transactional
    public VersionVue modifier(Long id, Saisie s) {
        VersionPlateforme v = trouver(id);
        if (repository.existsByNumeroIgnoreCaseAndIdNot(s.numero().trim(), id)) {
            throw new BusinessException("La version " + s.numero().trim() + " existe deja");
        }
        appliquer(v, s);
        return VersionVue.de(v);
    }

    @Transactional
    public void supprimer(Long id) {
        repository.delete(trouver(id));
    }

    private void appliquer(VersionPlateforme v, Saisie s) {
        v.setNumero(s.numero().trim());
        v.setTitre(s.titre().trim());
        v.setNotes(s.notes().trim());
        v.setRechargement(s.rechargement() == null || s.rechargement());
        v.setPublieeLe(s.publieeLe() == null ? (v.getPublieeLe() == null ? Instant.now() : v.getPublieeLe()) : s.publieeLe());
    }

    private VersionPlateforme trouver(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException("Version introuvable"));
    }
}
