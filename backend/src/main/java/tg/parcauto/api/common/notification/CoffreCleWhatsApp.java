package tg.parcauto.api.common.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Chiffrement de la cle d'API WAHA en base (AES-GCM) : une copie de la base ne suffit
 * pas a piloter le compte WhatsApp de la plateforme. Cle WHATSAPP_CLE, a defaut tiree
 * de la cle des jetons. Une valeur ancienne, encore en clair, reste lisible et sera
 * chiffree au prochain enregistrement.
 */
@Component
public class CoffreCleWhatsApp {

    private static final String PREFIXE = "v1:";
    private static final SecureRandom ALEA = new SecureRandom();

    private final SecretKeySpec cle;

    public CoffreCleWhatsApp(@Value("${WHATSAPP_CLE:${parcauto.security.jwt.secret}}") String secret) {
        try {
            this.cle = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest(("whatsapp-waha:" + secret).getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String chiffrer(String clair) {
        if (clair == null || clair.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[12];
            ALEA.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, cle, new GCMParameterSpec(128, iv));
            byte[] chiffre = c.doFinal(clair.getBytes(StandardCharsets.UTF_8));
            return PREFIXE + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + chiffre.length).put(iv).put(chiffre).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Valeur en clair ; nul si elle est illisible (cle de chiffrement changee). */
    public String dechiffrer(String stocke) {
        if (stocke == null || stocke.isBlank()) {
            return null;
        }
        if (!stocke.startsWith(PREFIXE)) {
            return stocke;
        }
        try {
            byte[] tout = Base64.getDecoder().decode(stocke.substring(PREFIXE.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, cle, new GCMParameterSpec(128, tout, 0, 12));
            return new String(c.doFinal(tout, 12, tout.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    public boolean estChiffree(String stocke) {
        return stocke != null && stocke.startsWith(PREFIXE);
    }
}
