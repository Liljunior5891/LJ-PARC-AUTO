package tg.parcauto.api.pointdevente.catalogue;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import tg.parcauto.api.common.SpecificationUtils;

import java.util.ArrayList;
import java.util.List;

public final class ArticleSpecifications {

    private ArticleSpecifications() {
    }

    public static Specification<Article> rechercher(String q, Long categorieId, Boolean actif, Boolean stockBas) {
        return (racine, requete, cb) -> {
            List<Predicate> predicats = new ArrayList<>();

            if (categorieId != null) {
                predicats.add(cb.equal(racine.get("categorie").get("id"), categorieId));
            }
            if (actif != null) {
                predicats.add(cb.equal(racine.get("actif"), actif));
            }
            if (Boolean.TRUE.equals(stockBas)) {
                predicats.add(cb.isTrue(racine.get("suiviStock")));
                predicats.add(cb.isNotNull(racine.get("seuilAlerte")));
                predicats.add(cb.lessThanOrEqualTo(racine.get("stockActuel"), racine.get("seuilAlerte")));
            }
            if (SpecificationUtils.renseigne(q)) {
                String motif = SpecificationUtils.motif(q);
                predicats.add(cb.or(
                        cb.like(cb.lower(racine.get("nom")), motif),
                        cb.like(cb.lower(racine.get("code")), motif),
                        cb.like(cb.lower(cb.coalesce(racine.get("codeBarres"), "")), motif)));
            }
            return predicats.isEmpty() ? cb.conjunction() : cb.and(predicats.toArray(Predicate[]::new));
        };
    }
}
