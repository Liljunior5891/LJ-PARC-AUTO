package tg.parcauto.api.agence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.agence.catalogue.InitialisationCatalogue;

/**
 * Premiere utilisation du module par une entreprise : reglages par defaut,
 * droits du cahier des charges et catalogue des operateurs. Sans effet ensuite.
 */
@Component
@RequiredArgsConstructor
public class InitialisationAgence {

    private final ReglageAgenceRepository reglages;
    private final DroitAgenceRepository droits;
    private final InitialisationCatalogue catalogue;

    @Transactional
    public ReglageAgence assurer() {
        return reglages.findFirstByOrderByIdAsc().orElseGet(() -> {
            ReglageAgence reglage = reglages.saveAndFlush(new ReglageAgence());
            if (droits.count() == 0) {
                for (PermissionAgence p : PermissionAgence.values()) {
                    p.getParDefaut().forEach(role -> droits.save(new DroitAgence(role, p)));
                }
            }
            catalogue.remplirSiVide();
            return reglage;
        });
    }
}
