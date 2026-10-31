package tg.parcauto.api.plateforme.module;

public class ModuleNonActiveException extends RuntimeException {

    public ModuleNonActiveException(ModuleApplicatif module) {
        super("Le module " + module.getLibelle() + " n'est pas active pour votre entreprise");
    }

    /** Module actif, mais dans une autre variante que celle de l'ecran demande. */
    public ModuleNonActiveException(ModuleApplicatif module, String variante) {
        super("Cette fonction du module " + module.getLibelle() + " demande le type d'activite "
                + module.getVariantes().stream()
                        .filter(v -> v.code().equals(variante))
                        .map(ModuleApplicatif.Variante::libelle)
                        .findFirst()
                        .orElse(variante));
    }
}
