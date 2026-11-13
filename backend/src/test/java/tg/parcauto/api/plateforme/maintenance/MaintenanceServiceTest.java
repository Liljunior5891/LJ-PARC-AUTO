package tg.parcauto.api.plateforme.maintenance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.plateforme.module.ModuleApplicatif;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaintenanceServiceTest {

    private MaintenanceRepository repository;
    private MaintenanceService service;
    private final Instant maintenant = Instant.now();

    @BeforeEach
    void preparer() {
        repository = mock(MaintenanceRepository.class);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new MaintenanceService(repository);
    }

    private static Maintenance maintenance(Instant debut, Instant fin, ModuleApplicatif... modules) {
        Maintenance m = new Maintenance();
        m.setMessage("Migration");
        m.setModules(EnumSet.of(modules[0], modules));
        m.setAnnonceLe(debut.minus(Duration.ofHours(24)));
        m.setDebut(debut);
        m.setFinPrevue(fin);
        return m;
    }

    @Test
    @DisplayName("Le statut suit l'annonce, le debut, la fin prevue ou anticipee")
    void statuts() {
        Maintenance m = maintenance(maintenant.plus(Duration.ofHours(2)), maintenant.plus(Duration.ofHours(4)),
                ModuleApplicatif.POINT_DE_VENTE);
        assertThat(m.statut(maintenant)).isEqualTo(Maintenance.Statut.ANNONCEE);
        assertThat(m.statut(maintenant.minus(Duration.ofDays(2)))).isEqualTo(Maintenance.Statut.PROGRAMMEE);
        assertThat(m.statut(maintenant.plus(Duration.ofHours(3)))).isEqualTo(Maintenance.Statut.EN_COURS);
        assertThat(m.statut(maintenant.plus(Duration.ofHours(4)))).isEqualTo(Maintenance.Statut.TERMINEE);
        m.setTermineeLe(maintenant.plus(Duration.ofHours(2).plusMinutes(30)));
        assertThat(m.statut(maintenant.plus(Duration.ofHours(3)))).isEqualTo(Maintenance.Statut.TERMINEE);
    }

    @Test
    @DisplayName("Seuls les modules vises sont en maintenance, et seulement pendant la plage")
    void enCoursParModule() {
        Maintenance enCours = maintenance(maintenant.minus(Duration.ofMinutes(5)), maintenant.plus(Duration.ofHours(1)),
                ModuleApplicatif.POINT_DE_VENTE, ModuleApplicatif.MARKETPLACE);
        Maintenance aVenir = maintenance(maintenant.plus(Duration.ofHours(1)), maintenant.plus(Duration.ofHours(2)),
                ModuleApplicatif.AGENCES);
        when(repository.aVenirOuEnCours(any())).thenReturn(List.of(enCours, aVenir));

        assertThat(service.enCours(ModuleApplicatif.POINT_DE_VENTE)).isPresent();
        assertThat(service.enCours(ModuleApplicatif.MARKETPLACE)).isPresent();
        assertThat(service.enCours(ModuleApplicatif.AGENCES)).isEmpty();
        assertThat(service.enCours(ModuleApplicatif.GESTION_PARC)).isEmpty();
        assertThat(service.annonces()).hasSize(2);
        // La liste est gardee en memoire : une seule lecture de la base.
        verify(repository, times(1)).aVenirOuEnCours(any());
    }

    @Test
    @DisplayName("Annonce la veille par defaut ; dates incoherentes refusees")
    void programmation() {
        Instant debut = maintenant.plus(Duration.ofDays(2));
        MaintenanceService.MaintenanceVue vue = service.programmer(new MaintenanceService.Saisie(" Mise a jour ",
                List.of(ModuleApplicatif.PRESENCE), debut, debut.plus(Duration.ofHours(1)), null));
        assertThat(vue.annonceLe()).isEqualTo(debut.minus(Duration.ofHours(24)));
        assertThat(vue.message()).isEqualTo("Mise a jour");
        assertThat(vue.statut()).isEqualTo(Maintenance.Statut.PROGRAMMEE);

        assertThatThrownBy(() -> service.programmer(new MaintenanceService.Saisie("x", List.of(ModuleApplicatif.PRESENCE),
                debut, debut, 0))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.programmer(new MaintenanceService.Saisie("x", List.of(ModuleApplicatif.PRESENCE),
                maintenant.minus(Duration.ofHours(2)), maintenant.minus(Duration.ofHours(1)), 0)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.programmer(new MaintenanceService.Saisie("x", List.of(ModuleApplicatif.PRESENCE),
                debut, debut.plus(Duration.ofDays(8)), 0))).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Une maintenance en cours se termine, une maintenance a venir s'annule")
    void terminerEtAnnuler() {
        Maintenance enCours = maintenance(maintenant.minus(Duration.ofMinutes(5)), maintenant.plus(Duration.ofHours(1)),
                ModuleApplicatif.AGENCES);
        Maintenance aVenir = maintenance(maintenant.plus(Duration.ofHours(1)), maintenant.plus(Duration.ofHours(2)),
                ModuleApplicatif.AGENCES);
        when(repository.findById(1L)).thenReturn(Optional.of(enCours));
        when(repository.findById(2L)).thenReturn(Optional.of(aVenir));

        assertThatThrownBy(() -> service.annuler(1L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.terminer(2L)).isInstanceOf(BusinessException.class);
        assertThat(service.terminer(1L).statut()).isEqualTo(Maintenance.Statut.TERMINEE);
        assertThat(service.annuler(2L).statut()).isEqualTo(Maintenance.Statut.ANNULEE);
    }
}
