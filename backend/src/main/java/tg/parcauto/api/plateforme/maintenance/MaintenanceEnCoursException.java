package tg.parcauto.api.plateforme.maintenance;

import lombok.Getter;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;

import java.time.Instant;

/** Ecran d'un module en maintenance : l'API repond 503 avec l'heure de retour prevue. */
@Getter
public class MaintenanceEnCoursException extends RuntimeException {

    private final ModuleApplicatif module;
    private final Instant finPrevue;

    public MaintenanceEnCoursException(ModuleApplicatif module, Maintenance m) {
        super(m.getMessage());
        this.module = module;
        this.finPrevue = m.finEffective();
    }
}
