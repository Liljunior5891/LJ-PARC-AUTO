package tg.parcauto.api.plateforme.tenant;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Entreprise pour le compte de laquelle le fil courant travaille.
 * <p>
 * Le filtre d'authentification la pose a partir du jeton et la retire en fin
 * de requete. Hibernate la lit a l'ouverture de chaque session pour cloisonner
 * les donnees : sans entreprise, aucune donnee metier n'est visible.
 * <p>
 * Le mode systeme leve le cloisonnement ; il est reserve aux traitements de la
 * plateforme qui doivent ecrire pour une entreprise qui n'existe pas encore dans
 * le contexte, comme l'inscription.
 */
public final class ContexteEntreprise {

    private static final ThreadLocal<Long> COURANTE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SYSTEME = new ThreadLocal<>();

    private ContexteEntreprise() {
    }

    public static void definir(Long entrepriseId) {
        COURANTE.set(entrepriseId);
    }

    public static void effacer() {
        COURANTE.remove();
        SYSTEME.remove();
    }

    public static Optional<Long> courante() {
        return Optional.ofNullable(COURANTE.get());
    }

    /** Entreprise courante, exigee : un traitement metier hors entreprise est une erreur de programmation. */
    public static Long exiger() {
        Long id = COURANTE.get();
        if (id == null) {
            throw new IllegalStateException("Aucune entreprise n'est associee au traitement en cours");
        }
        return id;
    }

    public static boolean systeme() {
        return Boolean.TRUE.equals(SYSTEME.get());
    }

    /**
     * Execute un traitement sans cloisonnement. Les transactions doivent s'ouvrir
     * a l'interieur : la session Hibernate fixe l'entreprise a son ouverture.
     */
    public static <T> T enModeSysteme(Supplier<T> traitement) {
        Boolean precedent = SYSTEME.get();
        SYSTEME.set(Boolean.TRUE);
        try {
            return traitement.get();
        } finally {
            if (precedent == null) {
                SYSTEME.remove();
            } else {
                SYSTEME.set(precedent);
            }
        }
    }
}
