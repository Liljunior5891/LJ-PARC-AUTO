package tg.parcauto.api.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Codes a usage unique bases sur le temps (TOTP, RFC 6238) : 6 chiffres, pas de
 * 30 secondes, HMAC-SHA1. Compatibles avec Google Authenticator, Microsoft
 * Authenticator, FreeOTP, etc.
 */
public final class Totp {

    public static final int PAS_SECONDES = 30;
    /** Decalage d'horloge tolere : le pas precedent et le suivant. */
    private static final int TOLERANCE = 1;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {
    }

    /** Nouveau secret de 160 bits, en base32 (forme saisie dans l'application d'authentification). */
    public static String nouveauSecret() {
        byte[] octets = new byte[20];
        new SecureRandom().nextBytes(octets);
        return base32(octets);
    }

    /**
     * Pas de temps du code s'il est valide (et posterieur au dernier pas accepte, contre le
     * rejeu), sinon -1.
     */
    public static long verifier(String secretBase32, String code, Long dernierPasAccepte) {
        if (code == null || !code.matches("^\\d{6}$")) {
            return -1;
        }
        byte[] cle = debase32(secretBase32);
        long pas = Instant.now().getEpochSecond() / PAS_SECONDES;
        for (long p = pas - TOLERANCE; p <= pas + TOLERANCE; p++) {
            if (dernierPasAccepte != null && p <= dernierPasAccepte) {
                continue;
            }
            if (MessageDigest.isEqual(code(cle, p).getBytes(), code.getBytes())) {
                return p;
            }
        }
        return -1;
    }

    /** Code d'un pas donne (tests et verification). */
    public static String code(byte[] cle, long pas) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(cle, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(pas).array());
            int d = h[h.length - 1] & 0x0f;
            int binaire = ((h[d] & 0x7f) << 24) | ((h[d + 1] & 0xff) << 16) | ((h[d + 2] & 0xff) << 8) | (h[d + 3] & 0xff);
            return String.format("%06d", binaire % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Adresse otpauth:// a mettre dans le code QR que scanne l'application d'authentification. */
    public static String adresse(String emetteur, String compte, String secretBase32) {
        String e = encoder(emetteur);
        return "otpauth://totp/" + e + ":" + encoder(compte) + "?secret=" + secretBase32 + "&issuer=" + e
                + "&algorithm=SHA1&digits=6&period=" + PAS_SECONDES;
    }

    private static String encoder(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static String base32(byte[] octets) {
        StringBuilder sortie = new StringBuilder();
        int tampon = 0;
        int bits = 0;
        for (byte b : octets) {
            tampon = (tampon << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sortie.append(BASE32.charAt((tampon >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sortie.append(BASE32.charAt((tampon << (5 - bits)) & 31));
        }
        return sortie.toString();
    }

    public static byte[] debase32(String texte) {
        String t = texte.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer sortie = ByteBuffer.allocate(t.length() * 5 / 8);
        int tampon = 0;
        int bits = 0;
        for (char c : t.toCharArray()) {
            int v = BASE32.indexOf(c);
            if (v < 0) {
                throw new IllegalArgumentException("Secret base32 invalide");
            }
            tampon = (tampon << 5) | v;
            bits += 5;
            if (bits >= 8) {
                sortie.put((byte) ((tampon >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        return java.util.Arrays.copyOf(sortie.array(), sortie.position());
    }
}
