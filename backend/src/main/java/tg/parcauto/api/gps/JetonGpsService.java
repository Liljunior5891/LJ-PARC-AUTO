package tg.parcauto.api.gps;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tg.parcauto.api.plateforme.tenant.ContexteEntreprise;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Jeton des traceurs GPS de l'entreprise. Un boitier ou une passerelle
 * (Traccar) ne peut pas garder une session d'utilisateur qui expire : il envoie
 * ses positions avec ce jeton, qui ne vaut que pour son entreprise et ne
 * permet rien d'autre. Seule son empreinte est gardee ; le regenerer coupe
 * l'ancien.
 */
@Service
@RequiredArgsConstructor
public class JetonGpsService {

    private static final SecureRandom ALEA = new SecureRandom();

    private final JdbcTemplate jdbc;

    public record Etat(boolean actif, Instant creeLe, Instant dernierUsage) {
    }

    /** Nouveau jeton de l'entreprise courante, montre une seule fois. */
    public String generer() {
        Long entreprise = ContexteEntreprise.exiger();
        byte[] octets = new byte[24];
        ALEA.nextBytes(octets);
        String jeton = "gps_" + HexFormat.of().formatHex(octets);
        jdbc.update("delete from gps_jeton where entreprise_id = ?", entreprise);
        jdbc.update("insert into gps_jeton (entreprise_id, empreinte, cree_le) values (?, ?, ?)",
                entreprise, empreinte(jeton), Timestamp.from(Instant.now()));
        return jeton;
    }

    public Etat etat() {
        List<Etat> lignes = jdbc.query("select cree_le, dernier_usage from gps_jeton where entreprise_id = ?",
                (rs, i) -> new Etat(true, instant(rs.getTimestamp(1)), instant(rs.getTimestamp(2))),
                ContexteEntreprise.exiger());
        return lignes.isEmpty() ? new Etat(false, null, null) : lignes.get(0);
    }

    /** Entreprise a qui appartient ce jeton, ou null s'il n'est pas reconnu. */
    public Long entreprise(String jeton) {
        if (jeton == null || jeton.isBlank() || jeton.length() > 200) {
            return null;
        }
        String e = empreinte(jeton.trim());
        List<Long> trouves = jdbc.query("select entreprise_id from gps_jeton where empreinte = ?",
                (rs, i) -> rs.getLong(1), e);
        if (trouves.isEmpty()) {
            return null;
        }
        jdbc.update("update gps_jeton set dernier_usage = ? where empreinte = ?", Timestamp.from(Instant.now()), e);
        return trouves.get(0);
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    static String empreinte(String jeton) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jeton.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
