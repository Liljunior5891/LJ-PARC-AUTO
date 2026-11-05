package tg.parcauto.api.parc.document;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class DocumentSpecifications {

    private DocumentSpecifications() {
    }

    /** Documents non archives, filtres par proprietaire et par type. */
    public static Specification<Document> rechercher(ProprietaireDocument proprietaireType,
                                                     Long proprietaireId,
                                                     TypeDocument type) {
        return (racine, requete, cb) -> {
            List<Predicate> predicats = new ArrayList<>();
            predicats.add(cb.isFalse(racine.get("archive")));

            if (proprietaireType != null) {
                predicats.add(cb.equal(racine.get("proprietaireType"), proprietaireType));
            } else {
                // Sans proprietaire precise, c'est la liste du parc : les pieces des clients du PDV n'y figurent pas.
                predicats.add(cb.notEqual(racine.get("proprietaireType"), ProprietaireDocument.CLIENT_PDV));
            }
            if (proprietaireId != null) {
                predicats.add(cb.equal(racine.get("proprietaireId"), proprietaireId));
            }
            if (type != null) {
                predicats.add(cb.equal(racine.get("type"), type));
            }
            return cb.and(predicats.toArray(Predicate[]::new));
        };
    }
}
