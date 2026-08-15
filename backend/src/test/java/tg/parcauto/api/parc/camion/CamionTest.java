package tg.parcauto.api.parc.camion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CamionTest {

    @Test
    @DisplayName("Le kilometrage parcouru est la difference avec le compteur initial")
    void kilometrageParcouru() {
        Camion camion = new Camion();
        camion.setKilometrageInitial(120_000L);
        camion.setKilometrageActuel(129_000L);

        assertThat(camion.kilometrageParcouru()).isEqualTo(9_000L);
    }

    @Test
    @DisplayName("Seuls les camions disponibles sont affectables")
    void statutsAffectables() {
        assertThat(StatutCamion.DISPONIBLE.affectable()).isTrue();
        assertThat(StatutCamion.EN_MISSION.affectable()).isFalse();
        assertThat(StatutCamion.EN_MAINTENANCE.affectable()).isFalse();
    }

    @Test
    @DisplayName("Un camion vendu ou hors service n'est plus exploitable")
    void statutsExploitables() {
        assertThat(StatutCamion.EN_LOCATION.exploitable()).isTrue();
        assertThat(StatutCamion.IMMOBILISE.exploitable()).isTrue();
        assertThat(StatutCamion.VENDU.exploitable()).isFalse();
        assertThat(StatutCamion.HORS_SERVICE.exploitable()).isFalse();
    }
}
