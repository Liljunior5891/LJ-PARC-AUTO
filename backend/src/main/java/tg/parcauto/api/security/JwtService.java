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

    /** Emetteur et audience exiges : un jeton signe pour un autre usage n'ouvre rien ici. */
    static final String EMETTEUR = "lj-saas";
    static final String AUDIENCE = "lj-saas-api";

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
        Map<String, Object> claims = new java.util.HashMap<>(Map.of(
                "uid", principal.getId(),
                "nom", principal.getNomComplet(),
                "roles", roles,
                "ver", principal.getVersionJetons(),
                // Identifiant de la session : le verrou de l'ecran de caisse vaut pour elle seule.
                "sid", java.util.UUID.randomUUID().toString()));
        if (principal.getEntrepriseId() != null) {
            claims.put("eid", principal.getEntrepriseId());
        }
        return Jwts.builder()
                .issuer(EMETTEUR)
                .audience().add(AUDIENCE).and()
                .subject(principal.getUsername())
                .claims(claims)
                .issuedAt(Date.from(maintenant))
                .expiration(Date.from(maintenant.plus(expiration)))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /**
     * Lit un jeton signe en HS512 uniquement (algorithme epingle), emis par
     * cette API et destine a elle.
     */
    public Claims lire(String token) {
        var jws = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(EMETTEUR)
                .requireAudience(AUDIENCE)
                .build()
                .parseSignedClaims(token);
        if (!Jwts.SIG.HS512.getId().equals(jws.getHeader().getAlgorithm())) {
            throw new io.jsonwebtoken.security.SignatureException("Algorithme de signature refuse");
        }
        return jws.getPayload();
    }

    /** Audience du jeton d'attente entre le mot de passe et le code : il n'ouvre aucune session. */
    static final String AUDIENCE_DEUX_FACTEURS = "lj-saas-deux-facteurs";

    /** Jeton de 5 minutes prouvant que le mot de passe est bon, a echanger contre une session avec le code. */
    public String genererJetonDeuxFacteurs(UtilisateurPrincipal principal) {
        Instant maintenant = Instant.now();
        return Jwts.builder()
                .issuer(EMETTEUR)
                .audience().add(AUDIENCE_DEUX_FACTEURS).and()
                .subject(principal.getUsername())
                .claim("uid", principal.getId())
                .claim("ver", principal.getVersionJetons())
                .issuedAt(Date.from(maintenant))
                .expiration(Date.from(maintenant.plus(Duration.ofMinutes(5))))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public Claims lireJetonDeuxFacteurs(String token) {
        var jws = Jwts.parser().verifyWith(key).requireIssuer(EMETTEUR).requireAudience(AUDIENCE_DEUX_FACTEURS)
                .build().parseSignedClaims(token);
        if (!Jwts.SIG.HS512.getId().equals(jws.getHeader().getAlgorithm())) {
            throw new io.jsonwebtoken.security.SignatureException("Algorithme de signature refuse");
        }
        return jws.getPayload();
    }

    public String extraireEmail(String token) {
        return lire(token).getSubject();
    }

    public long expirationSecondes() {
        return expiration.toSeconds();
    }
}
