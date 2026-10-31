package tg.parcauto.api.security;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import tg.parcauto.api.common.SpecificationUtils;

import java.util.ArrayList;
import java.util.List;

public final class UtilisateurSpecifications {

    private UtilisateurSpecifications() {
    }

    /**
     * Recherche multicritere dans une entreprise : chaque critere nul est
     * simplement ignore, sauf l'entreprise, toujours imposee.
     */
    public static Specification<Utilisateur> rechercher(Long entrepriseId, String q, Role role, Boolean actif) {
        return (racine, requete, cb) -> {
            List<Predicate> predicats = new ArrayList<>();
            predicats.add(cb.equal(racine.get("entrepriseId"), entrepriseId));

            if (actif != null) {
                predicats.add(cb.equal(racine.get("actif"), actif));
            }
            if (role != null) {
                // La collection de roles impose une jointure : sans distinct, un
                // utilisateur remonterait autant de fois qu'il a de profils.
                if (requete != null) {
                    requete.distinct(true);
                }
                predicats.add(cb.equal(racine.join("roles"), role));
            }
            if (SpecificationUtils.renseigne(q)) {
                String motif = SpecificationUtils.motif(q);
                predicats.add(cb.or(
                        cb.like(cb.lower(racine.get("email")), motif),
                        cb.like(cb.lower(racine.get("nom")), motif),
                        cb.like(cb.lower(cb.coalesce(racine.get("prenom"), "")), motif)));
            }
            return predicats.isEmpty() ? cb.conjunction() : cb.and(predicats.toArray(Predicate[]::new));
        };
    }
}
