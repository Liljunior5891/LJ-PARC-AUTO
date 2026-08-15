package tg.parcauto.api.parc.affectation;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class AffectationSpecifications {

    private AffectationSpecifications() {
    }

    public static Specification<Affectation> rechercher(Long camionId, Long chauffeurId, Boolean enCours) {
        return (racine, requete, cb) -> {
            // Les jointures ne sont prechargees que pour la requete de contenu, pas pour le count.
            if (requete != null && Long.class != requete.getResultType()
                    && long.class != requete.getResultType()) {
                racine.fetch("camion", JoinType.INNER);
                racine.fetch("chauffeur", JoinType.INNER);
            }

            List<Predicate> predicats = new ArrayList<>();
            if (camionId != null) {
                predicats.add(cb.equal(racine.get("camion").get("id"), camionId));
            }
            if (chauffeurId != null) {
                predicats.add(cb.equal(racine.get("chauffeur").get("id"), chauffeurId));
            }
            if (enCours != null) {
                predicats.add(enCours ? cb.isNull(racine.get("dateFin")) : cb.isNotNull(racine.get("dateFin")));
            }
            return predicats.isEmpty() ? cb.conjunction() : cb.and(predicats.toArray(Predicate[]::new));
        };
    }
}
