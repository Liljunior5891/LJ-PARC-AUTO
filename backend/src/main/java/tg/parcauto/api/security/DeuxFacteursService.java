package tg.parcauto.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.LimiteurRequetes;
import tg.parcauto.api.common.ResourceNotFoundException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Double authentification : apres le mot de passe, un code a 6 chiffres donne par
 * une application d'authentification (ou un code de secours a usage unique).
 * <p>
 * Le secret est chiffre en base (AES-GCM, cle DEUX_FACTEURS_CLE, a defaut tiree de
 * la cle des jetons) ; les codes de secours sont haches. Obligatoire pour le
 * super-administrateur (reglage parcauto.securite.deux-facteurs-super-admin),
 * proposee a tous les comptes.
 */
@Service
@Transactional
public class DeuxFacteursService {

    private static final String EMETTEUR = "LJ SaaS";
    private static final int CODES_SECOURS = 8;
    private static final SecureRandom ALEA = new SecureRandom();

    private final UtilisateurRepository utilisateurs;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encodeur;
    private final LimiteurRequetes limiteur;
    private final SecretKeySpec cle;

    public DeuxFacteursService(UtilisateurRepository utilisateurs, JdbcTemplate jdbc, PasswordEncoder encodeur,
                               LimiteurRequetes limiteur,
                               @Value("${DEUX_FACTEURS_CLE:${parcauto.security.jwt.secret}}") String cle) {
        this.utilisateurs = utilisateurs;
        this.jdbc = jdbc;
        this.encodeur = encodeur;
        this.limiteur = limiteur;
        try {
            this.cle = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest(("deux-facteurs:" + cle).getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Etat(boolean actif, int codesSecoursRestants) {
    }

    public record Preparation(String secret, String adresse, String codeQrSvg) {
    }

    public record Activation(List<String> codesSecours) {
    }

    @Transactional(readOnly = true)
    public Etat etat(Long utilisateurId) {
        Utilisateur u = charger(utilisateurId);
        Integer restants = jdbc.queryForObject(
                "select count(*) from utilisateur_code_secours where utilisateur_id = ? and utilise_le is null",
                Integer.class, utilisateurId);
        return new Etat(u.isTotpActif(), restants == null ? 0 : restants);
    }

    /** Nouveau secret, pas encore actif : il s'active quand un premier code est verifie. */
    public Preparation preparer(Long utilisateurId) {
        Utilisateur u = charger(utilisateurId);
        if (u.isTotpActif()) {
            throw new BusinessException("La double authentification est deja active : desactivez-la d'abord");
        }
        String secret = Totp.nouveauSecret();
        u.setTotpSecret(chiffrer(secret));
        u.setTotpDernierPas(null);
        String adresse = Totp.adresse(EMETTEUR, u.getEmail(), secret);
        return new Preparation(secret, adresse, tg.parcauto.api.pointdevente.restauration.qr.CodeQr.svg(adresse));
    }

    /** Premier code verifie : la double authentification est active ; les codes de secours sont montres une fois. */
    public Activation activer(Long utilisateurId, String code) {
        Utilisateur u = charger(utilisateurId);
        if (u.isTotpActif()) {
            throw new BusinessException("La double authentification est deja active");
        }
        if (u.getTotpSecret() == null) {
            throw new BusinessException("Commencez par scanner le code QR");
        }
        exigerCode(u, code, false);
        u.setTotpActif(true);
        // La session courante reste ouverte : l'utilisateur doit pouvoir noter ses codes de secours.
        return new Activation(nouveauxCodesSecours(utilisateurId));
    }

    /** Desactivation : le mot de passe et un code valide sont exiges. */
    public void desactiver(Long utilisateurId, String motDePasse, String code) {
        Utilisateur u = charger(utilisateurId);
        if (!u.isTotpActif()) {
            return;
        }
        if (motDePasse == null || !encodeur.matches(motDePasse, u.getMotDePasse())) {
            throw new BusinessException("Mot de passe incorrect");
        }
        exigerCode(u, code, true);
        u.setTotpActif(false);
        u.setTotpSecret(null);
        u.setTotpDernierPas(null);
        jdbc.update("delete from utilisateur_code_secours where utilisateur_id = ?", utilisateurId);
    }

    /** Verification a la connexion : code de l'application, ou code de secours (consomme). */
    public void verifierConnexion(Long utilisateurId, String code) {
        exigerCode(charger(utilisateurId), code, true);
    }

    @Transactional(readOnly = true)
    public boolean actif(Long utilisateurId) {
        return charger(utilisateurId).isTotpActif();
    }

    private void exigerCode(Utilisateur u, String code, boolean secoursAccepte) {
        String cleLimite = "deux-facteurs:" + u.getId();
        limiteur.verifier(cleLimite, 5, Duration.ofMinutes(15),
                "Trop de codes faux : reessayez dans quelques minutes");
        String saisi = code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase();
        long pas = u.getTotpSecret() == null ? -1 : Totp.verifier(dechiffrer(u.getTotpSecret()), saisi, u.getTotpDernierPas());
        if (pas >= 0) {
            u.setTotpDernierPas(pas);
            limiteur.oublier(cleLimite);
            return;
        }
        if (secoursAccepte && saisi.matches("^[A-Z0-9]{8}$") && consommerCodeSecours(u.getId(), saisi)) {
            limiteur.oublier(cleLimite);
            return;
        }
        limiteur.noter(cleLimite);
        throw new BusinessException("Code incorrect ou expire");
    }

    private boolean consommerCodeSecours(Long utilisateurId, String saisi) {
        for (Map<String, Object> l : jdbc.queryForList(
                "select id, empreinte from utilisateur_code_secours where utilisateur_id = ? and utilise_le is null",
                utilisateurId)) {
            if (encodeur.matches(saisi, (String) l.get("empreinte"))) {
                return jdbc.update("update utilisateur_code_secours set utilise_le = now() where id = ? and utilise_le is null",
                        l.get("id")) == 1;
            }
        }
        return false;
    }

    private List<String> nouveauxCodesSecours(Long utilisateurId) {
        jdbc.update("delete from utilisateur_code_secours where utilisateur_id = ?", utilisateurId);
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < CODES_SECOURS; i++) {
            StringBuilder c = new StringBuilder();
            for (int j = 0; j < 8; j++) {
                c.append(alphabet.charAt(ALEA.nextInt(alphabet.length())));
            }
            codes.add(c.substring(0, 4) + "-" + c.substring(4));
            jdbc.update("insert into utilisateur_code_secours (utilisateur_id, empreinte) values (?, ?)",
                    utilisateurId, encodeur.encode(c.toString()));
        }
        return codes;
    }

    private Utilisateur charger(Long id) {
        return utilisateurs.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", id));
    }

    private String chiffrer(String clair) {
        try {
            byte[] iv = new byte[12];
            ALEA.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, cle, new GCMParameterSpec(128, iv));
            byte[] chiffre = c.doFinal(clair.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + chiffre.length).put(iv).put(chiffre).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private String dechiffrer(String stocke) {
        try {
            byte[] tout = Base64.getDecoder().decode(stocke);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, cle, new GCMParameterSpec(128, tout, 0, 12));
            return new String(c.doFinal(tout, 12, tout.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BusinessException("Double authentification illisible : demandez sa reinitialisation");
        }
    }
}
