package tg.parcauto.api.parc.camion;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import tg.parcauto.api.common.SpecificationUtils;

import java.util.ArrayList;
import java.util.List;

public final class CamionSpecifications {

    private CamionSpecifications() {
    }

    /** Recherche multicritere : chaque critere nul est simplement ignore. */
    public static Specification<Camion> rechercher(String q, StatutCamion statut, Boolean actif) {
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
                        cb.like(cb.lower(racine.get("immatriculation")), motif),
                        cb.like(cb.lower(cb.coalesce(racine.get("marque"), "")), motif),
                        cb.like(cb.lower(cb.coalesce(racine.get("modele"), "")), motif)));
            }
            return predicats.isEmpty() ? cb.conjunction() : cb.and(predicats.toArray(Predicate[]::new));
        };
    }
}
