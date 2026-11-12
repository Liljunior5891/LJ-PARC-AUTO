package tg.parcauto.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Verrou de l'ecran de caisse, exige par le serveur et non plus seulement par
 * le navigateur. Une session de caissier ou de serveur (point de vente) ou de
 * caissier d'agence ne sert a rien tant que le code PIN n'a pas ete saisi :
 * <ul>
 *   <li>au debut de chaque session (connexion) ;</li>
 *   <li>apres un verrouillage demande par l'ecran (bouton ou inactivite) ;</li>
 *   <li>apres le delai d'inactivite regle par l'administrateur, plus une marge,
 *       sans aucune requete (onglet ferme, jeton copie) ;</li>
 *   <li>apres un redemarrage du serveur (l'etat est en memoire).</li>
 * </ul>
 * Un jeton copie sur un poste de caisse laisse ouvert ne permet donc pas
 * d'encaisser sans le code PIN du caissier.
 */
@Service
@RequiredArgsConstructor
public class VerrouEcranService {

    /** Marge ajoutee au delai regle : l'ecran se verrouille de lui-meme avant le serveur. */
    static final Duration MARGE = Duration.ofMinutes(2);
    static final int MINUTES_PAR_DEFAUT = 5;
    private static final Duration CACHE_REGLAGES = Duration.ofMinutes(2);

    private static final Set<String> POSTES = Set.of("CAISSIER", "SERVEUR", "PREPARATEUR");

    private final JdbcTemplate jdbc;
    private Clock horloge = Clock.systemUTC();

    private record Etat(Instant activite, boolean verrouille) {
    }

    private record Delai(int minutes, Instant lu) {
    }

    private enum Famille { POINT_DE_VENTE, AGENCE }

    private final Map<String, Etat> sessions = new ConcurrentHashMap<>();
    private final Map<String, Delai> delais = new ConcurrentHashMap<>();

    void horloge(Clock horloge) {
        this.horloge = horloge;
    }

    /** Cle de la session : l'identifiant porte par le jeton, sinon le compte et l'heure d'emission. */
    public static String cle(Object sid, Long utilisateurId, java.util.Date emisLe) {
        if (sid instanceof String s && !s.isBlank()) {
            return s;
        }
        return "u" + utilisateurId + ":" + (emisLe == null ? 0 : emisLe.getTime());
    }

    /** Le compte travaille-t-il sur un ecran de caisse verrouille par code PIN ? */
    public boolean concerne(UtilisateurPrincipal compte) {
        return famille(compte) != null;
    }

    /**
     * Requete d'une session concernee : vrai si elle doit etre refusee. Sinon,
     * l'activite est notee.
     */
    public boolean verrouille(UtilisateurPrincipal compte, String cle) {
        Famille famille = famille(compte);
        if (famille == null) {
            return false;
        }
        Instant maintenant = horloge.instant();
        Duration limite = Duration.ofMinutes(minutes(compte.getEntrepriseId(), famille)).plus(MARGE);
        Etat etat = sessions.compute(cle, (k, e) -> {
            if (e == null || e.verrouille() || Duration.between(e.activite(), maintenant).compareTo(limite) > 0) {
                return new Etat(e == null ? maintenant : e.activite(), true);
            }
            return new Etat(maintenant, false);
        });
        return etat.verrouille();
    }

    /** Code PIN juste : la session reprend. */
    public void deverrouiller(String cle) {
        if (cle != null) {
            sessions.put(cle, new Etat(horloge.instant(), false));
        }
    }

    /** L'ecran s'est verrouille (bouton ou inactivite) : le serveur refuse jusqu'au code PIN. */
    public void verrouiller(String cle) {
        if (cle != null) {
            sessions.put(cle, new Etat(horloge.instant(), true));
        }
    }

    /** Sessions oubliees apres une journee sans activite (elles redeviennent verrouillees, ce qui est sur). */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void purger() {
        Instant seuil = horloge.instant().minus(Duration.ofHours(24));
        sessions.entrySet().removeIf(e -> e.getValue().activite().isBefore(seuil));
    }

    private Famille famille(UtilisateurPrincipal compte) {
        if (compte == null || compte.getEntrepriseId() == null) {
            return null;
        }
        Set<String> roles = compte.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .collect(Collectors.toSet());
        // Memes regles que l'ecran : poste de travail pur (caissier, serveur), ou caissier d'agence simple.
        if (!roles.isEmpty() && POSTES.containsAll(roles) && (roles.contains("CAISSIER") || roles.contains("SERVEUR"))) {
            return Famille.POINT_DE_VENTE;
        }
        if (roles.contains("CAISSIER_AGENCE") && !roles.contains("ADMIN") && !roles.contains("SUPERVISEUR_AGENCE")) {
            return Famille.AGENCE;
        }
        return null;
    }

    private int minutes(Long entrepriseId, Famille famille) {
        String cle = famille + ":" + entrepriseId;
        Instant maintenant = horloge.instant();
        Delai d = delais.get(cle);
        if (d == null || Duration.between(d.lu(), maintenant).compareTo(CACHE_REGLAGES) > 0) {
            String table = famille == Famille.AGENCE ? "agc_reglage" : "pdv_reglage";
            List<Integer> lus = jdbc.queryForList(
                    "select minutes_verrou_caisse from " + table + " where entreprise_id = ?", Integer.class, entrepriseId);
            int m = lus.isEmpty() || lus.get(0) == null || lus.get(0) <= 0 ? MINUTES_PAR_DEFAUT : lus.get(0);
            d = new Delai(m, maintenant);
            delais.put(cle, d);
        }
        return d.minutes();
    }
}
