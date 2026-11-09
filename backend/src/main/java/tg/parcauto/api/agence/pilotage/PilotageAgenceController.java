package tg.parcauto.api.agence.pilotage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.agence.Agence;
import tg.parcauto.api.agence.DroitsAgence;
import tg.parcauto.api.agence.JournalAudit;
import tg.parcauto.api.agence.JournalAuditRepository;
import tg.parcauto.api.agence.PerimetreAgence;
import tg.parcauto.api.agence.tresorerie.JourneeCaisse;
import tg.parcauto.api.common.SpecificationUtils;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;
import tg.parcauto.api.plateforme.module.ModuleRequis;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Tableau de bord des agences et consultation du journal d'audit. */
@RestController
@ModuleRequis(ModuleApplicatif.AGENCES)
@RequestMapping("/api/agences")
@RequiredArgsConstructor
@Tag(name = "Agences - tableau de bord et audit")
public class PilotageAgenceController {

    private final TableauBordAgenceService tableauBord;
    private final JournalAuditRepository journal;
    private final PerimetreAgence perimetre;

    public record LigneAudit(Long id, Instant date, Long agenceId, String agenceNom, String auteurNom, String action,
                             String objet, Long objetId, String description, String avant, String apres) {
    }

    @GetMapping("/tableau-bord")
    @PreAuthorize("@droitsAgence.a('TABLEAU_BORD') or @droitsAgence.a('SOLDES_CONSULTER')")
    @Operation(summary = "Tresorerie, activite, revenus et repartitions sur une periode de journees de caisse")
    public TableauBordAgenceService.TableauBord tableauBord(
            @RequestParam(required = false) Long agenceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long reseauId,
            @RequestParam(required = false) Long caissierId) {
        return tableauBord.calculer(new TableauBordAgenceService.Filtres(agenceId, du, au, serviceId, reseauId, caissierId));
    }

    @GetMapping("/audit")
    @PreAuthorize("@droitsAgence.a('AUDIT_CONSULTER')")
    @Transactional(readOnly = true)
    @Operation(summary = "Journal d'audit : agences visibles et parametrage commun, du plus recent au plus ancien")
    public Page<LigneAudit> audit(
            @RequestParam(required = false) Long agenceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(required = false) String objet,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 40) Pageable pageable) {
        List<Agence> visibles = perimetre.visibles();
        Map<Long, String> noms = visibles.stream().collect(Collectors.toMap(Agence::getId, Agence::getNom));
        Set<Long> agences = agenceId != null ? Set.of(perimetre.exiger(agenceId).getId()) : noms.keySet();
        boolean admin = DroitsAgence.estAdministrateur();
        Specification<JournalAudit> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (agenceId != null) {
                p.add(cb.equal(root.get("agenceId"), agenceId));
            } else if (!admin) {
                // Le superviseur voit ses agences et le parametrage commun, pas les autres agences.
                p.add(agences.isEmpty() ? cb.isNull(root.get("agenceId"))
                        : cb.or(cb.isNull(root.get("agenceId")), root.get("agenceId").in(agences)));
            }
            if (du != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("dateAction"), du.atStartOfDay(JourneeCaisse.FUSEAU).toInstant()));
            }
            if (au != null) {
                p.add(cb.lessThan(root.get("dateAction"), au.plusDays(1).atStartOfDay(JourneeCaisse.FUSEAU).toInstant()));
            }
            if (SpecificationUtils.renseigne(objet)) {
                p.add(cb.equal(root.get("objet"), objet.trim()));
            }
            if (SpecificationUtils.renseigne(q)) {
                String motif = SpecificationUtils.motif(q);
                p.add(cb.or(cb.like(cb.lower(root.get("description")), motif),
                        cb.like(cb.lower(cb.coalesce(root.get("auteurNom"), "")), motif)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Pageable trie = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "dateAction").and(Sort.by(Sort.Direction.DESC, "id")));
        return journal.findAll(spec, trie).map(l -> new LigneAudit(l.getId(), l.getDateAction(), l.getAgenceId(),
                l.getAgenceId() == null ? null : noms.get(l.getAgenceId()), l.getAuteurNom(), l.getAction(), l.getObjet(),
                l.getObjetId(), l.getDescription(), l.getAvant(), l.getApres()));
    }
}
