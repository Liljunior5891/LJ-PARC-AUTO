package tg.parcauto.api.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    @Test
    void leCodeSuitLeVecteurDeReferenceDeLaNorme() {
        // RFC 6238, annexe B : secret ASCII "12345678901234567890", T = 59 s -> 94287082 (8 chiffres).
        byte[] cle = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertThat(Totp.code(cle, 59 / Totp.PAS_SECONDES)).isEqualTo("287082");
        // T = 1111111109 s -> 07081804.
        assertThat(Totp.code(cle, 1111111109L / Totp.PAS_SECONDES)).isEqualTo("081804");
    }

    @Test
    void leBase32FaitLAllerRetour() {
        byte[] octets = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        String b32 = Totp.base32(octets);
        assertThat(b32).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.debase32(b32)).isEqualTo(octets);
    }

    @Test
    void leCodeCourantEstAccepteUneSeuleFois() {
        String secret = Totp.nouveauSecret();
        long pas = Instant.now().getEpochSecond() / Totp.PAS_SECONDES;
        String code = Totp.code(Totp.debase32(secret), pas);
        long accepte = Totp.verifier(secret, code, null);
        assertThat(accepte).isGreaterThanOrEqualTo(pas - 1);
        // Rejeu du meme code : refuse.
        assertThat(Totp.verifier(secret, code, accepte)).isEqualTo(-1);
        assertThat(Totp.verifier(secret, "abc123", null)).isEqualTo(-1);
    }

    @Test
    void ladresseOtpauthPorteLEmetteurEtLeSecret() {
        String adresse = Totp.adresse("LJ SaaS", "admin@exemple.tg", "ABCDEF");
        assertThat(adresse).startsWith("otpauth://totp/LJ%20SaaS:admin%40exemple.tg?secret=ABCDEF&issuer=LJ%20SaaS");
    }
}
