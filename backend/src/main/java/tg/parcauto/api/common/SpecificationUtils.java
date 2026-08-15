package tg.parcauto.api.common;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;

/**
 * Aides a la construction de predicats de recherche.
 * <p>
 * Les criteres optionnels sont filtres en Java plutot qu'exprimes par des
 * comparaisons {@code :param is null} en JPQL : PostgreSQL ne sait pas typer un
 * parametre nul isole et rejette la requete (« la fonction lower(bytea) n'existe pas »).
 */
public final class SpecificationUtils {

    private SpecificationUtils() {
    }

    /** Vrai si le terme de recherche est exploitable. */
    public static boolean renseigne(String valeur) {
        return valeur != null && !valeur.isBlank();
    }

    /** Motif {@code like} insensible a la casse, borne par des jokers. */
    public static String motif(String terme) {
        return "%" + terme.trim().toLowerCase() + "%";
    }

    /** {@code lower(colonne)}, pour comparer sans tenir compte de la casse. */
    public static Expression<String> minuscules(CriteriaBuilder cb, Expression<String> colonne) {
        return cb.lower(colonne);
    }
}
