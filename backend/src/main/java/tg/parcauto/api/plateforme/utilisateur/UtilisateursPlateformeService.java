package tg.parcauto.api.plateforme.utilisateur;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.ResourceNotFoundException;
import tg.parcauto.api.common.audit.JournalActiviteService;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.Utilisateur;
import tg.parcauto.api.security.UtilisateurRepository;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static tg.parcauto.api.security.UtilisateurDto.LONGUEUR_MOT_DE_PASSE;

/**
 * Comptes de toutes les entreprises, vus et geres par le super-administrateur :
 * fiche, activation, mot de passe provisoire, fermeture des sessions et retrait
 * de la double authentification (telephone perdu).
 * <p>
 * Les profils restent geres par l'administrateur de chaque entreprise. Les
 * comptes de la plateforme (sans entreprise) ne sont pas concernes. Chaque
 * action est ecrite dans le journal de l'entreprise : son administrateur la voit.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UtilisateursPlateformeService {

    private final JdbcTemplate jdbc;
    private final UtilisateurRepository repository;
    private final PasswordEncoder encodeur;
    private final JournalActiviteService journal;

    /** Caracteres d'un mot de passe genere, sans les signes que l'on confond (0/O, 1/l/I). */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    public record Ligne(Long id, String email, String nom, String prenom, String nomComplet, String telephone,
                        boolean actif, List<String> roles, List<String> rolesLibelles,
                        Long entrepriseId, String entrepriseNom, String entrepriseStatut,
                        boolean motDePasseAChanger, boolean deuxFacteurs, Instant creeLe, Instant derniereConnexion) {
    }

    public record Modification(@NotBlank @Email @Size(max = 150) String email,
                               @NotBlank @Size(max = 100) String nom,
                               @Size(max = 100) String prenom,
                               @Size(max = 30) String telephone) {
    }

    /** Mot de passe saisi par le super-administrateur ; vide, il est genere. */
    public record MotDePasse(@Size(max = 72) String motDePasse) {
    }

    /** Mot de passe provisoire, renvoye une seule fois pour etre transmis au titulaire. */
    public record MotDePasseProvisoire(String motDePasse) {
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Ligne> lister(String q, Long entrepriseId, Boolean actif, int page, int taille) {
        StringBuilder where = new StringBuilder(" where u.entreprise_id is not null");
        List<Object> params = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            where.append(" and (u.email ilike ? or u.nom ilike ? or coalesce(u.prenom, '') ilike ?"
                    + " or coalesce(u.telephone, '') ilike ? or e.nom ilike ?)");
            String motif = "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            for (int i = 0; i < 5; i++) {
                params.add(motif);
            }
        }
        if (entrepriseId != null) {
            where.append(" and u.entreprise_id = ?");
            params.add(entrepriseId);
        }
        if (actif != null) {
            where.append(" and u.actif = ?");
            params.add(actif);
        }
        String from = " from utilisateur u join entreprise e on e.id = u.entreprise_id";
        Long total = jdbc.queryForObject("select count(*)" + from + where, Long.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(Math.max(1, Math.min(taille, 200)));
        pageParams.add((long) Math.max(0, page) * Math.max(1, Math.min(taille, 200)));
        List<Ligne> lignes = jdbc.query("""
                select u.id, u.email, u.nom, u.prenom, u.telephone, u.actif, u.mot_de_passe_a_changer, u.totp_actif,
                       u.cree_le, u.entreprise_id, e.nom as entreprise_nom, e.statut as entreprise_statut,
                       (select string_agg(r.role, ',' order by r.role) from utilisateur_role r where r.utilisateur_id = u.id) as roles,
                       (select max(j.date_action) from journal_activite j
                         where j.entreprise_id = u.entreprise_id and j.module = 'CONNEXION' and j.action = 'CONNEXION'
                           and j.auteur_id = u.id) as derniere_connexion
                """ + from + where + " order by e.nom, u.nom, u.prenom limit ? offset ?",
                (rs, i) -> {
                    String roles = rs.getString("roles");
                    List<String> codes = roles == null ? List.of() : Arrays.asList(roles.split(","));
                    String prenom = rs.getString("prenom");
                    String nom = rs.getString("nom");
                    Timestamp cree = rs.getTimestamp("cree_le");
                    Timestamp connexion = rs.getTimestamp("derniere_connexion");
                    return new Ligne(rs.getLong("id"), rs.getString("email"), nom, prenom,
                            prenom == null || prenom.isBlank() ? nom : prenom + " " + nom, rs.getString("telephone"),
                            rs.getBoolean("actif"), codes, codes.stream().map(UtilisateursPlateformeService::libelle).toList(),
                            rs.getLong("entreprise_id"), rs.getString("entreprise_nom"), rs.getString("entreprise_statut"),
                            rs.getBoolean("mot_de_passe_a_changer"), rs.getBoolean("totp_actif"),
                            cree == null ? null : cree.toInstant(), connexion == null ? null : connexion.toInstant());
                }, pageParams.toArray());
        return new org.springframework.data.domain.PageImpl<>(lignes,
                org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.max(1, Math.min(taille, 200))),
                total == null ? 0 : total);
    }

    public void modifier(Long id, Modification m) {
        Utilisateur u = charger(id);
        String email = m.email().trim();
        repository.findByEmailIgnoreCase(email).filter(autre -> !autre.getId().equals(id)).ifPresent(autre -> {
            throw new BusinessException("Cette adresse est deja utilisee par un autre compte");
        });
        Map<String, String> avant = resume(u);
        if (!u.getEmail().equalsIgnoreCase(email)) {
            // Nouvelle adresse de connexion : les sessions de l'ancienne se ferment.
            u.invaliderJetons();
        }
        u.setEmail(email);
        u.setNom(m.nom().trim());
        u.setPrenom(vide(m.prenom()));
        u.setTelephone(vide(m.telephone()));
        tracer(u, "MODIFICATION", "fiche modifiee par l'administrateur de la plateforme", avant, resume(u));
    }

    public void changerActivation(Long id, boolean actif) {
        Utilisateur u = charger(id);
        if (u.isActif() == actif) {
            return;
        }
        u.setActif(actif);
        if (!actif) {
            u.invaliderJetons();
        }
        tracer(u, actif ? "ACTIVATION" : "DESACTIVATION",
                (actif ? "compte active" : "compte desactive") + " par l'administrateur de la plateforme", null, null);
    }

    /**
     * Nouveau mot de passe provisoire : a changer a la prochaine connexion, et toutes
     * les sessions ouvertes se ferment.
     */
    public MotDePasseProvisoire reinitialiserMotDePasse(Long id, String saisi) {
        String motDePasse = saisi == null || saisi.isBlank() ? generer() : saisi;
        if (motDePasse.length() < LONGUEUR_MOT_DE_PASSE) {
            throw new BusinessException("Le mot de passe doit contenir au moins " + LONGUEUR_MOT_DE_PASSE + " caracteres");
        }
        Utilisateur u = charger(id);
        u.setMotDePasse(encodeur.encode(motDePasse));
        u.setMotDePasseAChanger(true);
        u.invaliderJetons();
        tracer(u, "MOT_DE_PASSE", "mot de passe reinitialise par l'administrateur de la plateforme", null, null);
        return new MotDePasseProvisoire(motDePasse);
    }

    /** Ferme toutes les sessions du compte. */
    public void deconnecter(Long id) {
        Utilisateur u = charger(id);
        u.invaliderJetons();
        tracer(u, "DECONNEXION", "sessions fermees par l'administrateur de la plateforme", null, null);
    }

    /** Retire la double authentification (telephone perdu) ; le titulaire pourra la remettre. */
    public void retirerDeuxFacteurs(Long id) {
        Utilisateur u = charger(id);
        if (!u.isTotpActif() && u.getTotpSecret() == null) {
            return;
        }
        u.setTotpActif(false);
        u.setTotpSecret(null);
        u.setTotpDernierPas(null);
        jdbc.update("delete from utilisateur_code_secours where utilisateur_id = ?", id);
        u.invaliderJetons();
        tracer(u, "DEUX_FACTEURS", "double authentification retiree par l'administrateur de la plateforme", null, null);
    }

    /** Compte d'une entreprise : ceux de la plateforme ne se gerent pas ici. */
    private Utilisateur charger(Long id) {
        Utilisateur u = repository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Utilisateur", id));
        if (u.getEntrepriseId() == null || u.getRoles().contains(Role.SUPER_ADMIN)) {
            throw ResourceNotFoundException.of("Utilisateur", id);
        }
        return u;
    }

    private void tracer(Utilisateur u, String action, String quoi, Map<String, String> avant, Map<String, String> apres) {
        journal.administration(u.getEntrepriseId(), action, "Utilisateur", "Utilisateur", u.getId(),
                "Compte « " + u.nomComplet() + " » : " + quoi, avant, apres);
    }

    private static Map<String, String> resume(Utilisateur u) {
        return Map.of("email", u.getEmail(), "nom", u.getNom(),
                "prenom", u.getPrenom() == null ? "" : u.getPrenom(),
                "telephone", u.getTelephone() == null ? "" : u.getTelephone());
    }

    private static String libelle(String code) {
        try {
            return Role.valueOf(code).getLibelle();
        } catch (IllegalArgumentException e) {
            return code;
        }
    }

    private static String generer() {
        SecureRandom alea = new SecureRandom();
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            s.append(ALPHABET.charAt(alea.nextInt(ALPHABET.length())));
        }
        return s.toString();
    }

    private static String vide(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
