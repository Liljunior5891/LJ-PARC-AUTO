package tg.parcauto.api.presence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tg.parcauto.api.common.BusinessException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Secrets du pointage : empreinte des PIN, code QR qui change toutes les 30
 * secondes (meme principe que les codes de validation bancaires), jetons des
 * bornes, et blocage d'un appareil apres trop de PIN faux.
 */
@Component
public class SecuritePresence {

    /** Duree de vie d'un code QR affiche. */
    static final int PAS_SECONDES = 30;
    /** Un code scanne reste valable ce nombre de pas : le temps d'autoriser la position et de taper le PIN. */
    private static final int PAS_ACCEPTES = 6;
    private static final int ESSAIS_MAX = 5;
    private static final Duration BLOCAGE = Duration.ofMinutes(10);

    private final SecureRandom aleatoire = new SecureRandom();
    private final byte[] cle;
    private final Map<String, Essais> essais = new ConcurrentHashMap<>();

    public SecuritePresence(@Value("${parcauto.security.jwt.secret}") String secret) {
        this.cle = ("presence:" + secret).getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------- PIN

    /** Empreinte d'un PIN dans une entreprise : la meme pour le meme PIN, illisible sans le secret du serveur. */
    public String empreintePin(Long entrepriseId, String pin) {
        return hmac("SHA256", cle, (entrepriseId + ":" + pin).getBytes(StandardCharsets.UTF_8));
    }

    public String nouveauPin() {
        return String.format("%06d", aleatoire.nextInt(1_000_000));
    }

    public static boolean pinValide(String pin) {
        return pin != null && pin.matches("\\d{6}");
    }

    // ------------------------------------------------------------- jetons

    public String jetonAleatoire() {
        byte[] octets = new byte[16];
        aleatoire.nextBytes(octets);
        return HexFormat.of().formatHex(octets);
    }

    public String secretQr() {
        byte[] octets = new byte[20];
        aleatoire.nextBytes(octets);
        return HexFormat.of().formatHex(octets);
    }

    public String codeAppairage() {
        return String.format("%06d", aleatoire.nextInt(1_000_000));
    }

    public static String sha256(String valeur) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(valeur.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------- code QR qui change

    /** Code a 6 chiffres affiche en ce moment pour le lieu. */
    public String codeCourant(String secret) {
        return code(secret, Instant.now().getEpochSecond() / PAS_SECONDES);
    }

    /** Secondes avant le prochain changement de code. */
    public static int secondesRestantes() {
        return (int) (PAS_SECONDES - Instant.now().getEpochSecond() % PAS_SECONDES);
    }

    /** Vrai si le code a ete affiche il y a moins de trois minutes environ. */
    public boolean codeValide(String secret, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            return false;
        }
        long pas = Instant.now().getEpochSecond() / PAS_SECONDES;
        for (long p = pas + 1; p >= pas - PAS_ACCEPTES; p--) {
            if (MessageDigest.isEqual(code(secret, p).getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                return true;
            }
        }
        return false;
    }

    private String code(String secret, long pas) {
        byte[] condense = hmacOctets("SHA1", HexFormat.of().parseHex(secret), ByteBuffer.allocate(8).putLong(pas).array());
        int decalage = condense[condense.length - 1] & 0x0f;
        int binaire = ((condense[decalage] & 0x7f) << 24) | ((condense[decalage + 1] & 0xff) << 16)
                | ((condense[decalage + 2] & 0xff) << 8) | (condense[decalage + 3] & 0xff);
        return String.format("%06d", binaire % 1_000_000);
    }

    // ------------------------------------------------------------- blocage apres PIN faux

    /** Refuse un appareil bloque apres trop de PIN faux. */
    public void verifierEssais(String appareil) {
        Essais e = essais.get(appareil);
        if (e != null && e.bloqueJusqua != null && Instant.now().isBefore(e.bloqueJusqua)) {
            long minutes = Math.max(1, Duration.between(Instant.now(), e.bloqueJusqua).toMinutes() + 1);
            throw new BusinessException("Trop de PIN faux : reessayez dans " + minutes + " min");
        }
    }

    /** Un PIN faux de plus ; vrai si l'appareil vient d'etre bloque. */
    public boolean echec(String appareil) {
        Essais e = essais.compute(appareil, (k, v) -> v == null || (v.bloqueJusqua != null && Instant.now().isAfter(v.bloqueJusqua))
                ? new Essais() : v);
        e.nombre++;
        if (e.nombre >= ESSAIS_MAX) {
            e.bloqueJusqua = Instant.now().plus(BLOCAGE);
            return true;
        }
        return false;
    }

    public void succes(String appareil) {
        essais.remove(appareil);
    }

    private static final class Essais {
        private int nombre;
        private Instant bloqueJusqua;
    }

    // ------------------------------------------------------------- outils

    private static String hmac(String algo, byte[] cle, byte[] donnees) {
        return HexFormat.of().formatHex(hmacOctets(algo, cle, donnees));
    }

    private static byte[] hmacOctets(String algo, byte[] cle, byte[] donnees) {
        try {
            Mac mac = Mac.getInstance("Hmac" + algo);
            mac.init(new SecretKeySpec(cle, "Hmac" + algo));
            return mac.doFinal(donnees);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
