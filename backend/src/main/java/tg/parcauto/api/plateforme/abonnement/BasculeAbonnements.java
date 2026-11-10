package tg.parcauto.api.plateforme.abonnement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;

/**
 * Met en vigueur, au debut de leur periode, les renouvellements payes d'avance :
 * chaque nuit peu apres minuit, et au demarrage pour rattraper un arret.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BasculeAbonnements {

    private final AbonnementService service;

    @Scheduled(cron = "0 5 0 * * *")
    public void chaqueNuit() {
        basculer();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void auDemarrage() {
        basculer();
    }

    private void basculer() {
        try {
            int n = ContexteEntreprise.enModeSysteme(service::basculerPeriodes);
            if (n > 0) {
                log.info("{} abonnement(s) passe(s) a leur nouvelle periode payee", n);
            }
        } catch (RuntimeException e) {
            log.error("Bascule des abonnements impossible", e);
        }
    }
}
