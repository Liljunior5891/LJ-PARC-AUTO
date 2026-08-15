package tg.parcauto.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(@Value("${parcauto.security.jwt.secret}") String secret,
                      @Value("${parcauto.security.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    public String genererToken(UtilisateurPrincipal principal) {
        Instant maintenant = Instant.now();
        List<String> roles = principal.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .toList();
        return Jwts.builder()
                .subject(principal.getUsername())
                .claims(Map.of(
                        "uid", principal.getId(),
                        "nom", principal.getNomComplet(),
                        "roles", roles))
                .issuedAt(Date.from(maintenant))
                .expiration(Date.from(maintenant.plus(expiration)))
                .signWith(key)
                .compact();
    }

    public Claims lire(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public String extraireEmail(String token) {
        return lire(token).getSubject();
    }

    public long expirationSecondes() {
        return expiration.toSeconds();
    }
}
