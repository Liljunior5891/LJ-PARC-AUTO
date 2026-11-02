package tg.parcauto.api.common;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

/**
 * Canal d'un element de la gestion de parc : en ligne (ne d'une demande faite
 * dans la boutique en ligne) ou agence. Le canal est porte par le contrat de
 * location ; une recette ou une facture le tient de son contrat.
 */
public final class CanalSpecifications {

    private CanalSpecifications() {
    }

    /** Contrat en ligne (vrai) ou d'agence (faux) ; nul = pas de filtre. */
    public static <T> Specification<T> contrat(Boolean enLigne) {
        return (racine, requete, cb) -> enLigne == null ? cb.conjunction()
                : enLigne ? cb.isTrue(racine.get("enLigne")) : cb.isFalse(racine.get("enLigne"));
    }

    /**
     * Recette ou facture en ligne (vrai) ou d'agence (faux) : le canal est le sien,
     * ou celui de son contrat de location.
     */
    public static <T> Specification<T> parContrat(Boolean enLigne) {
        return (racine, requete, cb) -> {
            if (enLigne == null) {
                return cb.conjunction();
            }
            Join<Object, Object> location = racine.join("location", JoinType.LEFT);
            var contratEnLigne = cb.and(cb.isNotNull(location.get("id")), cb.isTrue(location.get("enLigne")));
            return enLigne ? cb.or(cb.isTrue(racine.get("enLigne")), contratEnLigne)
                    : cb.and(cb.isFalse(racine.get("enLigne")), cb.not(contratEnLigne));
        };
    }
}
