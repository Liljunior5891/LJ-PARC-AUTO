package tg.parcauto.api.parc.affectation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AffectationTest {

    private Affectation affectation(LocalDate debut, LocalDate fin) {
        Affectation a = new Affectation();
        a.setDateDebut(debut);
        a.setDateFin(fin);
        return a;
    }

    @Test
    @DisplayName("Une affectation sans date de fin est consideree en cours")
    void affectationOuverte() {
        assertThat(affectation(LocalDate.of(2026, 1, 1), null).enCours()).isTrue();
        assertThat(affectation(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)).enCours()).isFalse();
    }

    @Test
    @DisplayName("Deux periodes qui se recouvrent sont detectees")
    void chevauchementDetecte() {
        Affectation existante = affectation(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 20));

        assertThat(existante.chevauche(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 25))).isTrue();
        assertThat(existante.chevauche(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10))).isTrue();
        assertThat(existante.chevauche(LocalDate.of(2026, 1, 20), null)).isTrue();
    }

    @Test
    @DisplayName("Deux periodes disjointes ne se chevauchent pas")
    void periodesDisjointes() {
        Affectation existante = affectation(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 20));

        assertThat(existante.chevauche(LocalDate.of(2026, 1, 21), LocalDate.of(2026, 1, 30))).isFalse();
        assertThat(existante.chevauche(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 9))).isFalse();
    }

    @Test
    @DisplayName("Une affectation ouverte bloque toute periode posterieure")
    void affectationOuverteBloqueLaSuite() {
        Affectation ouverte = affectation(LocalDate.of(2026, 1, 10), null);

        assertThat(ouverte.chevauche(LocalDate.of(2026, 6, 1), null)).isTrue();
        assertThat(ouverte.chevauche(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5))).isFalse();
    }
}
