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
    /**
     * Un code scanne reste valable ce nombre de pas (60 a 90 s) : le temps d'autoriser la
     * position et de taper le PIN, mais trop court pour relayer une photo du code.
     */
    private static final int PAS_ACCEPTES = 2;
    /** Telephone (adresse) : PIN faux tolere dans la fenetre, avant blocage. */
    private static final int ESSAIS_MAX_TELEPHONE = 10;
    /** Borne partagee par tout le personnel : un peu plus large, et un blocage plus court. */
    private static final int ESSAIS_MAX_BORNE = 15;
    private static final Duration FENETRE = Duration.ofMinutes(10);
    private static final Duration BLOCAGE_TELEPHONE = Duration.ofMinutes(10);
    private static final Duration BLOCAGE_BORNE = Duration.ofMinutes(3);
    /** Blocage le plus long : chaque nouveau blocage triple la duree precedente, jusqu'a 24 h. */
    private static final Duration BLOCAGE_MAX = Duration.ofHours(24);
    /** Toute l'entreprise : PIN faux toleres par heure, tous appareils confondus. */
    private static final int ESSAIS_MAX_ENTREPRISE = 50;
    private static final Duration FENETRE_ENTREPRISE = Duration.ofHours(1);
    private static final Duration BLOCAGE_ENTREPRISE = Duration.ofMinutes(15);

    private final SecureRandom aleatoire = new SecureRandom();
    private final byte[] cle;
    private final Map<String, Essais> essais = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong numeros = new java.util.concurrent.atomic.AtomicLong();

    /**
     * Cle des empreintes de PIN : PRESENCE_CLE_PIN si definie, sinon (compatibilite des PIN
     * deja emis) la cle de signature des jetons.
     */
    public SecuritePresence(@Value("${PRESENCE_CLE_PIN:${parcauto.security.jwt.secret}}") String secret) {
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

    /**
     * Reserve un essai de PIN pour l'appareil, en une operation atomique : les
     * essais de la fenetre glissante sont comptes AVANT la verification, si bien
     * que des envois paralleles ne depassent pas la limite. Un essai reussi est
     * rendu ({@link #reussi}) ; un essai faux reste compte jusqu'a la fin de la
     * fenetre, meme si l'appareil tape ensuite un bon PIN.
     */
    public long reserverEssai(String appareil) {
        boolean borne = appareil.startsWith("borne:");
        return reserver(appareil, borne ? ESSAIS_MAX_BORNE : ESSAIS_MAX_TELEPHONE, FENETRE,
                borne ? BLOCAGE_BORNE : BLOCAGE_TELEPHONE,
                "Trop de PIN faux sur cet appareil : reessayez dans %d min");
    }

    /** Compteur commun a toute l'entreprise : une force brute repartie sur plusieurs appareils s'arrete aussi. */
    public long reserverEssaiEntreprise(Long entrepriseId) {
        return reserver("entreprise:" + entrepriseId, ESSAIS_MAX_ENTREPRISE, FENETRE_ENTREPRISE, BLOCAGE_ENTREPRISE,
                "Trop de PIN faux dans l'entreprise : le pointage par PIN reprend dans %d min. Prevenez le gerant.");
    }

    /** Codes QR faux : compteur a part et plus large (un code expire n'est pas un PIN faux). */
    public long reserverEssaiCode(String appareil) {
        return reserver("code:" + appareil, 30, FENETRE, BLOCAGE_TELEPHONE,
                "Trop de codes QR faux sur cet appareil : reessayez dans %d min");
    }

    private long reserver(String appareil, int maximum, Duration fenetre, Duration blocageBase, String message) {
        long numero = numeros.incrementAndGet();
        Instant maintenant = Instant.now();
        Instant[] bloque = {null};
        essais.compute(appareil, (k, v) -> {
            Essais e = v == null ? new Essais() : v;
            if (e.bloqueJusqua != null && maintenant.isBefore(e.bloqueJusqua)) {
                bloque[0] = e.bloqueJusqua;
                return e;
            }
            e.bloqueJusqua = null;
            e.essais.removeIf(t -> t.instant().isBefore(maintenant.minus(fenetre)));
            if (e.essais.size() >= maximum) {
                // Blocage progressif : 1x, 3x, 9x... la duree de base, jusqu'a 24 h.
                Duration blocage = blocageBase.multipliedBy((long) Math.pow(3, Math.min(e.niveau, 10)));
                if (blocage.compareTo(BLOCAGE_MAX) > 0) {
                    blocage = BLOCAGE_MAX;
                }
                e.niveau++;
                e.bloqueJusqua = maintenant.plus(blocage);
                e.essais.clear();
                bloque[0] = e.bloqueJusqua;
                return e;
            }
            e.essais.add(new Essai(numero, maintenant));
            return e;
        });
        if (bloque[0] != null) {
            long minutes = Math.max(1, Duration.between(maintenant, bloque[0]).toMinutes() + 1);
            throw new BusinessException(String.format(message, minutes));
        }
        return numero;
    }

    /** L'essai reserve etait le bon PIN : il ne compte pas. Les faux precedents restent comptes. */
    public void reussi(String appareil, long numero) {
        essais.computeIfPresent(appareil, (k, e) -> {
            e.essais.removeIf(t -> t.numero() == numero);
            return e.essais.isEmpty() && e.bloqueJusqua == null ? null : e;
        });
    }

    /** Oublie les appareils sans essai recent (la table ne grossit pas sans fin). */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 600_000)
    public void purger() {
        Instant limite = Instant.now().minus(BLOCAGE_MAX);
        essais.entrySet().removeIf(en -> {
            Essais e = en.getValue();
            synchronized (e) {
                return (e.bloqueJusqua == null || e.bloqueJusqua.isBefore(Instant.now()))
                        && e.essais.stream().allMatch(t -> t.instant().isBefore(limite));
            }
        });
    }

    private record Essai(long numero, Instant instant) {
    }

    private static final class Essais {
        private final java.util.List<Essai> essais = new java.util.ArrayList<>();
        private Instant bloqueJusqua;
        /** Nombre de blocages deja subis : la duree du suivant en depend. Oublie apres 24 h de calme. */
        private int niveau;
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
