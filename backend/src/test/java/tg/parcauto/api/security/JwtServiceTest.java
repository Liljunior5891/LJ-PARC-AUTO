package tg.parcauto.api.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "cle-de-test-suffisamment-longue-pour-hs512-0123456789-abcdefghijklmnopqrstuvwxyz";
    private final JwtService service = new JwtService(SECRET, 60);

    private static String jeton(String emetteur, String audience) {
        return Jwts.builder()
                .issuer(emetteur)
                .audience().add(audience).and()
                .subject("caisse@exemple.tg")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS512)
                .compact();
    }

    @Test
    void accepteLesJetonsNova() {
        assertThat(service.lire(jeton("nova", "nova-api")).getSubject()).isEqualTo("caisse@exemple.tg");
    }

    @Test
    void accepteEncoreLesJetonsEmisAvantLeRenommage() {
        assertThat(service.lire(jeton("lj-saas", "lj-saas-api")).getSubject()).isEqualTo("caisse@exemple.tg");
    }

    @Test
    void refuseUnAutreEmetteurOuUnMelange() {
        assertThatThrownBy(() -> service.lire(jeton("autre", "nova-api"))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.lire(jeton("nova", "lj-saas-api"))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.lire(jeton("nova", "nova-deux-facteurs"))).isInstanceOf(JwtException.class);
    }
}
