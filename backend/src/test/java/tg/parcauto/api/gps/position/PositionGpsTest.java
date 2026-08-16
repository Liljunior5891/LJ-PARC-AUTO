package tg.parcauto.api.gps.position;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PositionGpsTest {

    /** Lome, place de l'Independance. */
    private static final BigDecimal LOME_LAT = new BigDecimal("6.1319");
    private static final BigDecimal LOME_LON = new BigDecimal("1.2228");

    private PositionGps position(String latitude, String longitude, String vitesse) {
        PositionGps p = new PositionGps();
        p.setLatitude(new BigDecimal(latitude));
        p.setLongitude(new BigDecimal(longitude));
        if (vitesse != null) {
            p.setVitesseKmh(new BigDecimal(vitesse));
        }
        return p;
    }

    @Test
    @DisplayName("La distance entre deux points identiques est nulle")
    void distanceNulle() {
        PositionGps p = position("6.1319", "1.2228", null);

        assertThat(p.distanceMetres(LOME_LAT, LOME_LON)).isZero();
    }

    @Test
    @DisplayName("La distance Lome - Atakpame est proche de 160 km")
    void distanceConnue() {
        // Atakpame se situe a environ 160 km au nord de Lome.
        PositionGps lome = position("6.1319", "1.2228", null);
        double distance = lome.distanceMetres(new BigDecimal("7.5333"), new BigDecimal("1.1333"));

        assertThat(distance).isBetween(150_000d, 170_000d);
    }

    @Test
    @DisplayName("Un decalage d'un centieme de degre reste sous le kilometre et demi")
    void distanceCourte() {
        PositionGps p = position("6.1319", "1.2228", null);
        double distance = p.distanceMetres(new BigDecimal("6.1419"), LOME_LON);

        assertThat(distance).isBetween(1_000d, 1_500d);
    }

    @Test
    @DisplayName("Le vehicule n'est en mouvement qu'au-dela du seuil de bruit GPS")
    void mouvement() {
        assertThat(position("6.13", "1.22", "0").enMouvement()).isFalse();
        assertThat(position("6.13", "1.22", "2.5").enMouvement()).isFalse();
        assertThat(position("6.13", "1.22", "45").enMouvement()).isTrue();
        assertThat(position("6.13", "1.22", null).enMouvement()).isFalse();
    }
}
