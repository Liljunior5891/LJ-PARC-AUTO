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

    /** Longueur maximale d'un terme de recherche : au-dela, il est coupe. */
    public static final int LONGUEUR_MAX_TERME = 100;

    /**
     * Motif {@code like} insensible a la casse, borne par des jokers. Les jokers
     * saisis ({@code %}, {@code _}) sont echappes par la barre oblique inverse,
     * caractere d'echappement par defaut de PostgreSQL : ils cherchent le caractere
     * lui-meme, et un terme fait de jokers ne declenche pas une recherche couteuse.
     */
    public static String motif(String terme) {
        return "%" + echapper(terme) + "%";
    }

    /** Terme coupe a {@link #LONGUEUR_MAX_TERME}, en minuscules, jokers echappes. */
    public static String echapper(String terme) {
        String t = terme.trim().toLowerCase();
        if (t.length() > LONGUEUR_MAX_TERME) {
            t = t.substring(0, LONGUEUR_MAX_TERME);
        }
        return t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** {@code lower(colonne)}, pour comparer sans tenir compte de la casse. */
    public static Expression<String> minuscules(CriteriaBuilder cb, Expression<String> colonne) {
        return cb.lower(colonne);
    }
}
