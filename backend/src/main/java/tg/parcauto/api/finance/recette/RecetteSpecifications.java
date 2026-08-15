package tg.parcauto.api.finance.recette;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import tg.parcauto.api.common.SpecificationUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class RecetteSpecifications {

    private RecetteSpecifications() {
    }

    public static Specification<Recette> rechercher(String q, TypeRecette type, Long camionId,
                                                    Long clientId, LocalDate debut, LocalDate fin) {
        return (racine, requete, cb) -> {
            boolean requeteDeContenu = requete == null
                    || (Long.class != requete.getResultType() && long.class != requete.getResultType());
            if (requeteDeContenu) {
                racine.fetch("camion", JoinType.INNER);
                racine.fetch("client", JoinType.LEFT);
            }

            List<Predicate> predicats = new ArrayList<>();
            if (type != null) {
                predicats.add(cb.equal(racine.get("type"), type));
            }
            if (camionId != null) {
                predicats.add(cb.equal(racine.get("camion").get("id"), camionId));
            }
            if (clientId != null) {
                predicats.add(cb.equal(racine.get("client").get("id"), clientId));
            }
            if (debut != null) {
                predicats.add(cb.greaterThanOrEqualTo(racine.get("dateRecette"), debut));
            }
            if (fin != null) {
                predicats.add(cb.lessThanOrEqualTo(racine.get("dateRecette"), fin));
            }
            if (SpecificationUtils.renseigne(q)) {
                String motif = SpecificationUtils.motif(q);
                predicats.add(cb.or(
                        cb.like(cb.lower(racine.get("numero")), motif),
                        cb.like(cb.lower(racine.get("libelle")), motif),
                        cb.like(cb.lower(cb.coalesce(racine.get("reference"), "")), motif),
                        cb.like(cb.lower(racine.get("camion").get("immatriculation")), motif)));
            }
            return predicats.isEmpty() ? cb.conjunction() : cb.and(predicats.toArray(Predicate[]::new));
        };
    }
}
