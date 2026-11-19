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

    @Test
    void jetonAssistanceLimiteAUneEntrepriseEtADeuxHeures() {
        Utilisateur u = new Utilisateur();
        u.setId(1L);
        u.setEmail("superadmin@nova.tg");
        u.setNom("Admin");
        u.setMotDePasse("x");
        u.setRoles(java.util.EnumSet.of(Role.SUPER_ADMIN));
        UtilisateurPrincipal superAdmin = new UtilisateurPrincipal(u, null, true);

        var claims = service.lire(service.genererTokenAssistance(superAdmin, 42L, "CONSULTATION"));

        assertThat(claims.getSubject()).isEqualTo("superadmin@nova.tg");
        assertThat(((Number) claims.get("eid")).longValue()).isEqualTo(42L);
        assertThat(claims.get("assist")).isEqualTo("CONSULTATION");
        assertThat(claims.getExpiration().toInstant())
                .isBefore(Instant.now().plus(JwtService.DUREE_ASSISTANCE).plusSeconds(5));

        UtilisateurPrincipal p = UtilisateurPrincipal.pourAssistance(superAdmin, 42L, "Boutique", "CONSULTATION");
        assertThat(p.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        assertThat(p.getNomComplet()).startsWith("Assistance Nova");
        assertThat(p.enAssistance()).isTrue();
    }
}
