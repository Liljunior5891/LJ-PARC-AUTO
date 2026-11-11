package tg.parcauto.api.plateforme.abonnement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DureeAbonnementTest {

    @Test
    void uneDureeLibreRecoitLaRemiseDeLaTrancheAtteinte() {
        assertThat(Periodicite.pour(1)).isEqualTo(Periodicite.MENSUEL);
        assertThat(Periodicite.pour(2)).isEqualTo(Periodicite.MENSUEL);
        assertThat(Periodicite.pour(5)).isEqualTo(Periodicite.TRIMESTRIEL);
        assertThat(Periodicite.pour(9)).isEqualTo(Periodicite.SEMESTRIEL);
        assertThat(Periodicite.pour(24)).isEqualTo(Periodicite.ANNUEL);
    }

    @Test
    void laDureePayeeEstLaDureeLibreSinonCelleDeLaPeriodicite() {
        PaiementAbonnement p = new PaiementAbonnement();
        p.setPeriodicite(Periodicite.TRIMESTRIEL);
        assertThat(p.dureeMois()).isEqualTo(3);
        assertThat(p.libelleDuree()).isEqualTo("Trimestriel");

        p.setMois(5);
        assertThat(p.dureeMois()).isEqualTo(5);
        assertThat(p.libelleDuree()).isEqualTo("5 mois");

        p.setMois(3);
        assertThat(p.libelleDuree()).isEqualTo("Trimestriel");
    }
}
