package tg.parcauto.api.plateforme.module;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Rattache un controleur a un module : ses points d'entree repondent 403 tant
 * que l'entreprise de l'utilisateur ne l'a pas active.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ModuleRequis {

    ModuleApplicatif value();

    /**
     * Variante exigee en plus du module (par exemple les tables d'un restaurant) ;
     * vide, toutes les variantes du module sont acceptees.
     */
    String variante() default "";

    /**
     * Point d'entree ouvert sans compte (la carte d'un restaurant vue par ses
     * clients) : le module et sa variante restent exiges pour l'entreprise
     * designee, mais aucun profil n'est demande.
     */
    boolean publique() default false;
}
