package tg.parcauto.api.plateforme;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;
import tg.parcauto.api.common.BaseEntity;
import tg.parcauto.api.plateforme.module.ModuleRequis;
import tg.parcauto.api.plateforme.tenant.DiffusionEntreprise;
import tg.parcauto.api.plateforme.tenant.ResolveurEntreprise;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CloisonnementTest {

    /** Paquets des modules : tout controleur qui s'y trouve doit exiger son module. */
    private static final List<String> PAQUETS_MODULE_PARC = List.of(
            "tg.parcauto.api.parc", "tg.parcauto.api.exploitation", "tg.parcauto.api.finance",
            "tg.parcauto.api.gps", "tg.parcauto.api.maintenance", "tg.parcauto.api.pilotage",
            "tg.parcauto.api.pointdevente");

    @Test
    @DisplayName("Chaque controleur d'un module declare le module qu'il exige")
    void controleursRattachesAUnModule() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        int controles = 0;
        for (String paquet : PAQUETS_MODULE_PARC) {
            for (var candidat : scanner.findCandidateComponents(paquet)) {
                Class<?> classe = Class.forName(candidat.getBeanClassName());
                assertThat(classe.isAnnotationPresent(ModuleRequis.class))
                        .as("%s doit porter @ModuleRequis", classe.getSimpleName())
                        .isTrue();
                controles++;
            }
        }
        assertThat(controles).isGreaterThan(15);
    }

    @Test
    @DisplayName("Les entites metier heritent du socle cloisonne")
    void entitesMetierCloisonnees() throws NoSuchFieldException {
        assertThat(BaseEntity.class.getDeclaredField("entrepriseId")
                .isAnnotationPresent(org.hibernate.annotations.TenantId.class)).isTrue();
    }

    @Test
    @DisplayName("Sans entreprise dans le contexte, Hibernate ne voit aucune donnee metier")
    void horsContexteRienNEstVisible() {
        ResolveurEntreprise resolveur = new ResolveurEntreprise();
        ContexteEntreprise.effacer();
        assertThat(resolveur.resolveCurrentTenantIdentifier()).isEqualTo(ResolveurEntreprise.AUCUNE);
        assertThat(resolveur.isRoot(ResolveurEntreprise.AUCUNE)).isFalse();

        ContexteEntreprise.definir(7L);
        try {
            assertThat(resolveur.resolveCurrentTenantIdentifier()).isEqualTo(7L);
            assertThat(ContexteEntreprise.enModeSysteme(resolveur::resolveCurrentTenantIdentifier))
                    .isEqualTo(ResolveurEntreprise.RACINE);
            // Le mode systeme ne survit pas au traitement qui l'a demande.
            assertThat(resolveur.resolveCurrentTenantIdentifier()).isEqualTo(7L);
        } finally {
            ContexteEntreprise.effacer();
        }
    }

    @Test
    @DisplayName("Un abonnement temps reel n'est accepte que sur le canal de sa propre entreprise")
    void canauxTempsReel() {
        assertThat(DiffusionEntreprise.appartientA("/topic/entreprises/3/gps/positions", 3L)).isTrue();
        assertThat(DiffusionEntreprise.appartientA("/topic/entreprises/3/gps/positions", 4L)).isFalse();
        assertThat(DiffusionEntreprise.appartientA("/topic/entreprises/31/incidents", 3L)).isFalse();
        assertThat(DiffusionEntreprise.appartientA("/topic/gps/positions", 3L)).isFalse();
        assertThat(DiffusionEntreprise.appartientA("/topic/entreprises/3", null)).isFalse();
    }
}
