package tg.parcauto.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Code PIN de l'utilisateur connecte, qui deverrouille son ecran de caisse.
 * <p>
 * Apres {@value #ESSAIS_MAX} erreurs, le deverrouillage est bloque
 * {@value #MINUTES_BLOCAGE} minutes : il faut alors se reconnecter avec le mot
 * de passe, ou attendre.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PinService {

    static final int ESSAIS_MAX = 5;
    static final int MINUTES_BLOCAGE = 15;

    private final UtilisateurRepository repository;
    private final PasswordEncoder encodeur;

    private record Echecs(int nombre, Instant jusqua) {
    }

    private final Map<Long, Echecs> echecs = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public boolean defini(Long utilisateurId) {
        return charger(utilisateurId).getPinHash() != null;
    }

    /** Premier code, choisi par le caissier lui-meme : seulement s'il n'en a pas encore. */
    public void creer(Long utilisateurId, String pin) {
        Utilisateur u = charger(utilisateurId);
        if (u.getPinHash() != null) {
            throw new BusinessException("Un code PIN existe deja : demandez a votre gerant de le reinitialiser");
        }
        u.setPinHash(encodeur.encode(pin));
    }

    /**
     * Le caissier change lui-meme son code depuis « Mon compte » : l'ancien code
     * est exige (memes limites d'essais que le deverrouillage).
     */
    public void changer(Long utilisateurId, String actuel, String nouveau) {
        verifier(utilisateurId, actuel);
        if (actuel.equals(nouveau)) {
            throw new BusinessException("Le nouveau code doit etre different de l'actuel");
        }
        charger(utilisateurId).setPinHash(encodeur.encode(nouveau));
    }

    /**
     * Chaque essai est compte avant d'etre verifie, en une operation atomique :
     * des essais envoyes en parallele ne peuvent pas depasser la limite.
     */
    @Transactional(readOnly = true)
    public void verifier(Long utilisateurId, String pin) {
        Instant maintenant = Instant.now();
        Echecs essai = echecs.compute(utilisateurId, (id, e) -> {
            if (e != null && e.jusqua() != null && maintenant.isBefore(e.jusqua())) {
                return e;
            }
            int nombre = (e == null || e.jusqua() != null ? 0 : e.nombre()) + 1;
            return nombre > ESSAIS_MAX ? new Echecs(nombre, maintenant.plus(Duration.ofMinutes(MINUTES_BLOCAGE)))
                    : new Echecs(nombre, null);
        });
        if (essai.jusqua() != null) {
            throw new BusinessException("Trop d'essais : reconnectez-vous avec votre mot de passe");
        }
        Utilisateur u = charger(utilisateurId);
        if (u.getPinHash() == null) {
            throw new BusinessException("Aucun code PIN n'est defini pour ce compte");
        }
        if (encodeur.matches(pin, u.getPinHash())) {
            echecs.remove(utilisateurId);
            return;
        }
        if (essai.nombre() >= ESSAIS_MAX) {
            echecs.put(utilisateurId, new Echecs(essai.nombre(), Instant.now().plus(Duration.ofMinutes(MINUTES_BLOCAGE))));
            throw new BusinessException("Trop d'essais : reconnectez-vous avec votre mot de passe");
        }
        throw new BusinessException("Code PIN incorrect (" + (ESSAIS_MAX - essai.nombre()) + " essai(s) restant(s))");
    }

    /** Reconnexion par mot de passe : les essais de PIN repartent de zero. */
    public void debloquer(Long utilisateurId) {
        echecs.remove(utilisateurId);
    }

    private Utilisateur charger(Long id) {
        return repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", id));
    }
}
