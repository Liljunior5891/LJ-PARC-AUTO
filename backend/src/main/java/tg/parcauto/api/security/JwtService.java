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
    static final String EMETTEUR = "nova";
    static final String AUDIENCE = "nova-api";

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

    /** Duree d'une session d'assistance : au-dela, le super-administrateur la rouvre. */
    public static final Duration DUREE_ASSISTANCE = Duration.ofHours(2);

    /**
     * Jeton d'assistance : le super-administrateur (compte {@code superAdmin}) dans l'espace
     * d'une entreprise. Le filtre relit le compte et exige qu'il soit toujours super-administrateur.
     */
    public String genererTokenAssistance(UtilisateurPrincipal superAdmin, Long entrepriseId, String mode) {
        Instant maintenant = Instant.now();
        return Jwts.builder()
                .issuer(EMETTEUR)
                .audience().add(AUDIENCE).and()
                .subject(superAdmin.getUsername())
                .claims(Map.of(
                        "uid", superAdmin.getId(),
                        "nom", superAdmin.getNomComplet(),
                        "roles", List.of("ADMIN"),
                        "ver", superAdmin.getVersionJetons(),
                        "sid", java.util.UUID.randomUUID().toString(),
                        "eid", entrepriseId,
                        "assist", mode))
                .issuedAt(Date.from(maintenant))
                .expiration(Date.from(maintenant.plus(DUREE_ASSISTANCE)))
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
                .build()
                .parseSignedClaims(token);
        if (!Jwts.SIG.HS512.getId().equals(jws.getHeader().getAlgorithm())) {
            throw new io.jsonwebtoken.security.SignatureException("Algorithme de signature refuse");
        }
        Claims claims = jws.getPayload();
        if (!emetteurAccepte(claims)) {
            throw new io.jsonwebtoken.IncorrectClaimException(jws.getHeader(), claims, "iss", claims.getIssuer(),
                    "Emetteur ou audience du jeton refuse");
        }
        return claims;
    }

    /**
     * Emetteur et audience de cette API. Les jetons emis avant le renommage de la plateforme
     * (lj-saas) restent valables jusqu'a leur expiration : une caisse avec des ventes hors
     * ligne en attente n'est pas deconnectee par la mise a jour.
     */
    static boolean emetteurAccepte(Claims claims) {
        java.util.Set<String> audience = claims.getAudience() == null ? java.util.Set.of() : claims.getAudience();
        return (EMETTEUR.equals(claims.getIssuer()) && audience.contains(AUDIENCE))
                || (ANCIEN_EMETTEUR.equals(claims.getIssuer()) && audience.contains(ANCIENNE_AUDIENCE)
                    && java.time.Instant.now().isBefore(FIN_ANCIEN_EMETTEUR));
    }

    /** Ancien nom de la plateforme : a retirer une fois expires les jetons emis avant le renommage. */
    static final String ANCIEN_EMETTEUR = "lj-saas";
    static final String ANCIENNE_AUDIENCE = "lj-saas-api";
    /** Au-dela, l'ancien emetteur n'est plus accepte du tout (les jetons durent 8 h au plus). */
    static final java.time.Instant FIN_ANCIEN_EMETTEUR = java.time.Instant.parse("2027-01-31T00:00:00Z");

    /** Audience du jeton d'attente entre le mot de passe et le code : il n'ouvre aucune session. */
    static final String AUDIENCE_DEUX_FACTEURS = "nova-deux-facteurs";

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
