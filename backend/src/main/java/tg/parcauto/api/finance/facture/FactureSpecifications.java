package tg.parcauto.api.finance.facture;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import tg.parcauto.api.common.SpecificationUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class FactureSpecifications {

    private FactureSpecifications() {
    }

    public static Specification<Facture> rechercher(String q, StatutFacture statut, Long clientId,
                                                    LocalDate debut, LocalDate fin) {
        return (racine, requete, cb) -> {
            boolean requeteDeContenu = requete == null
                    || (Long.class != requete.getResultType() && long.class != requete.getResultType());
            if (requeteDeContenu) {
                racine.fetch("client", JoinType.INNER);
            }

            List<Predicate> predicats = new ArrayList<>();
            if (statut != null) {
                predicats.add(cb.equal(racine.get("statut"), statut));
            }
            if (clientId != null) {
                predicats.add(cb.equal(racine.get("client").get("id"), clientId));
            }
            if (debut != null) {
                predicats.add(cb.greaterThanOrEqualTo(racine.get("dateEmission"), debut));
            }
            if (fin != null) {
                predicats.add(cb.lessThanOrEqualTo(racine.get("dateEmission"), fin));
            }
            if (SpecificationUtils.renseigne(q)) {
                String motif = SpecificationUtils.motif(q);
                predicats.add(cb.or(
                        cb.like(cb.lower(racine.get("numero")), motif),
                        cb.like(cb.lower(racine.get("client").get("raisonSociale")), motif)));
            }
            return predicats.isEmpty() ? cb.conjunction() : cb.and(predicats.toArray(Predicate[]::new));
        };
    }
}
