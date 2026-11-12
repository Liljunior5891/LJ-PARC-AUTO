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
    private final tg.parcauto.api.common.audit.JournalActiviteService journal;

    public record Etat(boolean actif, Instant creeLe, Instant dernierUsage) {
    }

    /** Vehicule equipe d'un traceur, et l'etat de son jeton propre. */
    public record Traceur(Long camionId, String immatriculation, String deviceId, boolean actif,
                          Instant creeLe, Instant dernierUsage) {
    }

    /**
     * Origine d'un envoi : l'entreprise, et le vehicule si le jeton est celui d'un
     * traceur (null pour le jeton de passerelle de l'entreprise).
     */
    public record Source(Long entreprise, Long camionId) {
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
        journal.administration(entreprise, "JETON_GPS", "JetonGps", "Jeton GPS", null,
                "Jeton GPS genere (l'ancien est coupe)", null, null);
        return jeton;
    }

    /** Revoque le jeton sans en creer d'autre : plus aucune passerelle n'envoie de positions. */
    public void revoquer() {
        Long entreprise = ContexteEntreprise.exiger();
        jdbc.update("delete from gps_jeton where entreprise_id = ?", entreprise);
        journal.administration(entreprise, "JETON_GPS", "JetonGps", "Jeton GPS", null,
                "Jeton GPS revoque", null, null);
    }

    public Etat etat() {
        List<Etat> lignes = jdbc.query("select cree_le, dernier_usage from gps_jeton where entreprise_id = ?",
                (rs, i) -> new Etat(true, instant(rs.getTimestamp(1)), instant(rs.getTimestamp(2))),
                ContexteEntreprise.exiger());
        return lignes.isEmpty() ? new Etat(false, null, null) : lignes.get(0);
    }

    // ---------------------------------------------------------------- jetons par traceur

    /** Vehicules equipes d'un traceur, avec l'etat de leur jeton. */
    public List<Traceur> traceurs() {
        return jdbc.query("""
                select c.id, c.immatriculation, c.gps_device_id, t.cree_le, t.dernier_usage, t.camion_id is not null
                from camion c left join gps_jeton_traceur t on t.camion_id = c.id
                where c.entreprise_id = ? and c.gps_device_id is not null and c.gps_device_id <> ''
                order by c.immatriculation""",
                (rs, i) -> new Traceur(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(6),
                        instant(rs.getTimestamp(4)), instant(rs.getTimestamp(5))),
                ContexteEntreprise.exiger());
    }

    /** Nouveau jeton du traceur d'un vehicule, montre une seule fois ; l'ancien est coupe. */
    public String genererTraceur(Long camionId) {
        Long entreprise = ContexteEntreprise.exiger();
        String immatriculation = immatriculationEquipee(camionId, entreprise);
        byte[] octets = new byte[24];
        ALEA.nextBytes(octets);
        String jeton = "gpt_" + HexFormat.of().formatHex(octets);
        jdbc.update("delete from gps_jeton_traceur where camion_id = ?", camionId);
        jdbc.update("insert into gps_jeton_traceur (camion_id, entreprise_id, empreinte, cree_le) values (?, ?, ?, ?)",
                camionId, entreprise, empreinte(jeton), Timestamp.from(Instant.now()));
        journal.administration(entreprise, "JETON_GPS", "Camion", immatriculation, camionId,
                "Jeton du traceur genere (l'ancien est coupe)", null, null);
        return jeton;
    }

    /** Revoque le jeton d'un traceur : il ne peut plus envoyer de positions. */
    public void revoquerTraceur(Long camionId) {
        Long entreprise = ContexteEntreprise.exiger();
        String immatriculation = immatriculationEquipee(camionId, entreprise);
        jdbc.update("delete from gps_jeton_traceur where camion_id = ? and entreprise_id = ?", camionId, entreprise);
        journal.administration(entreprise, "JETON_GPS", "Camion", immatriculation, camionId,
                "Jeton du traceur revoque", null, null);
    }

    private String immatriculationEquipee(Long camionId, Long entreprise) {
        List<String> trouves = jdbc.queryForList(
                "select immatriculation from camion where id = ? and entreprise_id = ?"
                        + " and gps_device_id is not null and gps_device_id <> ''",
                String.class, camionId, entreprise);
        if (trouves.isEmpty()) {
            throw new tg.parcauto.api.common.BusinessException(
                    "Renseignez d'abord l'identifiant du traceur sur la fiche du vehicule");
        }
        return trouves.get(0);
    }

    /** Origine d'un jeton : traceur d'un vehicule, ou passerelle de l'entreprise ; null s'il est inconnu. */
    public Source source(String jeton) {
        if (jeton == null || jeton.isBlank() || jeton.length() > 200) {
            return null;
        }
        String e = empreinte(jeton.trim());
        List<Source> traceur = jdbc.query("select entreprise_id, camion_id from gps_jeton_traceur where empreinte = ?",
                (rs, i) -> new Source(rs.getLong(1), rs.getLong(2)), e);
        if (!traceur.isEmpty()) {
            // Date du dernier usage rafraichie au plus une fois par minute.
            Instant maintenant = Instant.now();
            jdbc.update("update gps_jeton_traceur set dernier_usage = ? where empreinte = ?"
                            + " and (dernier_usage is null or dernier_usage < ?)",
                    Timestamp.from(maintenant), e, Timestamp.from(maintenant.minusSeconds(60)));
            return traceur.get(0);
        }
        Long entreprise = entreprise(jeton);
        return entreprise == null ? null : new Source(entreprise, null);
    }

    /** Entreprise a qui appartient ce jeton de passerelle, ou null s'il n'est pas reconnu. */
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
        // Date du dernier usage rafraichie au plus une fois par minute (pas une ecriture par envoi).
        Instant maintenant = Instant.now();
        jdbc.update("update gps_jeton set dernier_usage = ? where empreinte = ?"
                        + " and (dernier_usage is null or dernier_usage < ?)",
                Timestamp.from(maintenant), e, Timestamp.from(maintenant.minusSeconds(60)));
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
