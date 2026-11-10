package tg.parcauto.api.presence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PresenceLogiqueTest {

    private final SecuritePresence securite = new SecuritePresence("secret-de-test");

    private static PointagePresence pointage(LocalDate jour, LocalTime heure) {
        PointagePresence p = new PointagePresence();
        p.setJour(jour);
        p.setArriveeLe(ZonedDateTime.of(jour, heure, PointageService.FUSEAU).toInstant());
        return p;
    }

    /** Lundi. */
    private static final LocalDate LUNDI = LocalDate.of(2026, 11, 9);

    @Test
    @DisplayName("Dans la tolerance : a l'heure ; au-dela : retard compte depuis l'heure prevue")
    void retard() {
        ReglagePresence r = new ReglagePresence();
        PointagePresence aLHeure = pointage(LUNDI, LocalTime.of(8, 7));
        PointageService.calculerRetard(aLHeure, r);
        assertThat(aLHeure.getRetardMinutes()).isZero();

        PointagePresence enRetard = pointage(LUNDI, LocalTime.of(8, 15));
        PointageService.calculerRetard(enRetard, r);
        assertThat(enRetard.getRetardMinutes()).isEqualTo(15);
        assertThat(enRetard.isApresLimite()).isFalse();
    }

    @Test
    @DisplayName("Apres l'heure limite : accepte comme un gros retard ; un jour non travaille n'a pas de retard")
    void apresLimiteEtRepos() {
        ReglagePresence r = new ReglagePresence();
        PointagePresence tard = pointage(LUNDI, LocalTime.of(13, 0));
        PointageService.calculerRetard(tard, r);
        assertThat(tard.getRetardMinutes()).isEqualTo(300);
        assertThat(tard.isApresLimite()).isTrue();

        PointagePresence dimanche = pointage(LUNDI.minusDays(1), LocalTime.of(10, 0));
        PointageService.calculerRetard(dimanche, r);
        assertThat(dimanche.getRetardMinutes()).isZero();
    }

    @Test
    @DisplayName("Distance GPS : quelques dizaines de metres a Lome")
    void distance() {
        double d = PointageService.distanceMetres(6.1375, 1.2123, 6.1378, 1.2125);
        assertThat(d).isBetween(30.0, 50.0);
    }

    @Test
    @DisplayName("Le code QR affiche est valable, un code invente ne l'est pas")
    void codeQr() {
        String secret = securite.secretQr();
        assertThat(securite.codeValide(secret, securite.codeCourant(secret))).isTrue();
        assertThat(securite.codeValide(secret, "12345")).isFalse();
        assertThat(securite.codeValide(securite.secretQr(), securite.codeCourant(secret))).isFalse();
    }

    @Test
    @DisplayName("Le meme PIN donne la meme empreinte dans une entreprise, une autre ailleurs")
    void empreintePin() {
        assertThat(securite.empreintePin(1L, "123456")).isEqualTo(securite.empreintePin(1L, "123456"));
        assertThat(securite.empreintePin(1L, "123456")).isNotEqualTo(securite.empreintePin(2L, "123456"));
        assertThat(SecuritePresence.pinValide("12345")).isFalse();
        assertThat(SecuritePresence.pinValide("012345")).isTrue();
    }

    @Test
    @DisplayName("Apres 10 PIN faux, l'appareil est bloque ; son propre bon PIN n'efface pas les faux")
    void blocage() {
        for (int i = 0; i < 9; i++) {
            securite.reserverEssai("ip:1");
        }
        // Un bon PIN au milieu : il ne compte pas, mais les 9 faux restent.
        securite.reussi("ip:1", securite.reserverEssai("ip:1"));
        securite.reserverEssai("ip:1");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> securite.reserverEssai("ip:1"))
                .hasMessageContaining("Trop de PIN faux");
        securite.reserverEssai("ip:2");
    }

    @Test
    @DisplayName("La borne partagee tolere plus d'erreurs avant de se bloquer")
    void blocageBorne() {
        for (int i = 0; i < 15; i++) {
            securite.reserverEssai("borne:1");
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> securite.reserverEssai("borne:1"))
                .hasMessageContaining("Trop de PIN faux");
    }
}
