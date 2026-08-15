package tg.parcauto.api.finance.depense;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import tg.parcauto.api.common.SpecificationUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class DepenseSpecifications {

    private DepenseSpecifications() {
    }

    public static Specification<Depense> rechercher(String q, CategorieDepense categorie,
                                                    SousCategorieDepense sousCategorie, Long camionId,
                                                    Long chauffeurId, LocalDate debut, LocalDate fin) {
        return (racine, requete, cb) -> {
            boolean requeteDeContenu = requete == null
                    || (Long.class != requete.getResultType() && long.class != requete.getResultType());
            if (requeteDeContenu) {
                racine.fetch("camion", JoinType.INNER);
                racine.fetch("chauffeur", JoinType.LEFT);
            }

            List<Predicate> predicats = new ArrayList<>();
            if (categorie != null) {
                predicats.add(cb.equal(racine.get("categorie"), categorie));
            }
            if (sousCategorie != null) {
                predicats.add(cb.equal(racine.get("sousCategorie"), sousCategorie));
            }
            if (camionId != null) {
                predicats.add(cb.equal(racine.get("camion").get("id"), camionId));
            }
            if (chauffeurId != null) {
                predicats.add(cb.equal(racine.get("chauffeur").get("id"), chauffeurId));
            }
            if (debut != null) {
                predicats.add(cb.greaterThanOrEqualTo(racine.get("dateDepense"), debut));
            }
            if (fin != null) {
                predicats.add(cb.lessThanOrEqualTo(racine.get("dateDepense"), fin));
            }
            if (SpecificationUtils.renseigne(q)) {
                String motif = SpecificationUtils.motif(q);
                predicats.add(cb.or(
                        cb.like(cb.lower(racine.get("numero")), motif),
                        cb.like(cb.lower(racine.get("libelle")), motif),
                        cb.like(cb.lower(cb.coalesce(racine.get("fournisseur"), "")), motif),
                        cb.like(cb.lower(cb.coalesce(racine.get("station"), "")), motif),
                        cb.like(cb.lower(racine.get("camion").get("immatriculation")), motif)));
            }
            return predicats.isEmpty() ? cb.conjunction() : cb.and(predicats.toArray(Predicate[]::new));
        };
    }
}
