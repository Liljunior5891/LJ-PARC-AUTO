package tg.parcauto.api.plateforme.module;

/**
 * Point d'extension d'un module : il refuse un changement de variante qui
 * laisserait des donnees en plan (par exemple quitter le mode restaurant avec
 * des tables encore ouvertes) en levant une {@code BusinessException}.
 * <p>
 * Appele dans le contexte de l'entreprise concernee.
 */
public interface GardeVariante {

    ModuleApplicatif module();

    void verifierChangement(String actuelle, String nouvelle);
}
