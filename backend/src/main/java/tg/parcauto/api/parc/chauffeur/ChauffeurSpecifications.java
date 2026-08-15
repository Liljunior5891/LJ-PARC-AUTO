package tg.parcauto.api.parc.chauffeur;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import tg.parcauto.api.common.SpecificationUtils;

import java.util.ArrayList;
import java.util.List;

public final class ChauffeurSpecifications {

    private ChauffeurSpecifications() {
    }

    public static Specification<Chauffeur> rechercher(String q, StatutChauffeur statut, Boolean actif) {
        return (racine, requete, cb) -> {
            List<Predicate> predicats = new ArrayList<>();

            if (statut != null) {
                predicats.add(cb.equal(racine.get("statut"), statut));
            }
            if (actif != null) {
                predicats.add(cb.equal(racine.get("actif"), actif));
            }
            if (SpecificationUtils.renseigne(q)) {
                String motif = SpecificationUtils.motif(q);
                predicats.add(cb.or(
                        cb.like(cb.lower(racine.get("nom")), motif),
                        cb.like(cb.lower(racine.get("prenom")), motif),
                        cb.like(cb.lower(racine.get("matricule")), motif),
                        cb.like(cb.lower(racine.get("telephone")), motif)));
            }
            return predicats.isEmpty() ? cb.conjunction() : cb.and(predicats.toArray(Predicate[]::new));
        };
    }
}
