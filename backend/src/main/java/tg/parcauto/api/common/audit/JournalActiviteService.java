package tg.parcauto.api.common.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lecture du journal d'audit des modules, et trace des connexions. */
@Service
@RequiredArgsConstructor
public class JournalActiviteService {

    private static final ZoneId FUSEAU = ZoneId.of("Africa/Lome");

    private final JournalActiviteRepository repository;
    private final EcritureJournal ecriture;
    private final ObjectMapper json;

    public record Filtres(String module, String action, String objet, Long auteurId, LocalDate du, LocalDate au,
                          String recherche, Long objetId) {
    }

    /** Un champ change : son nom lisible, sa valeur avant et apres. */
    public record Changement(String champ, String libelle, String avant, String apres) {
    }

    public record LigneDto(Long id, Instant date, Long auteurId, String auteurNom, String module, String action, String objet,
                           String objetLibelle, Long objetId, String description, String adresseIp, List<Changement> changements) {
    }

    public record Choix(String valeur, String libelle) {
    }

    public record Referentiels(List<Choix> objets, List<Choix> auteurs) {
    }

    @Transactional(readOnly = true)
    public Page<LigneDto> lister(Filtres f, int page, int taille) {
        Specification<JournalActivite> spec = (racine, requete, cb) -> {
            List<Predicate> p = new ArrayList<>();
            // Un ou plusieurs modules separes par des virgules (la gestion de parc en regroupe plusieurs).
            if (f.module() != null && !f.module().isBlank()) {
                p.add(racine.get("module").in(List.of(f.module().split("\\s*,\\s*"))));
            }
            if (f.action() != null && !f.action().isBlank()) {
                p.add(cb.equal(racine.get("action"), f.action()));
            }
            if (f.objet() != null && !f.objet().isBlank()) {
                p.add(cb.equal(racine.get("objet"), f.objet()));
            }
            if (f.objetId() != null) {
                p.add(cb.equal(racine.get("objetId"), f.objetId()));
            }
            if (f.auteurId() != null) {
                p.add(cb.equal(racine.get("auteurId"), f.auteurId()));
            }
            if (f.du() != null) {
                p.add(cb.greaterThanOrEqualTo(racine.get("dateAction"), f.du().atStartOfDay(FUSEAU).toInstant()));
            }
            if (f.au() != null) {
                p.add(cb.lessThan(racine.get("dateAction"), f.au().plusDays(1).atStartOfDay(FUSEAU).toInstant()));
            }
            if (f.recherche() != null && !f.recherche().isBlank()) {
                String motif = "%" + f.recherche().trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.lower(racine.get("description")), motif), cb.like(cb.lower(racine.get("auteurNom")), motif)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return repository.findAll(spec, PageRequest.of(page, Math.min(Math.max(taille, 1), 100),
                        Sort.by(Sort.Direction.DESC, "dateAction").and(Sort.by(Sort.Direction.DESC, "id"))))
                .map(this::versDto);
    }

    @Transactional(readOnly = true)
    public Referentiels referentiels() {
        return new Referentiels(
                repository.objets().stream().map(r -> new Choix((String) r[0], (String) r[1])).toList(),
                repository.auteurs().stream().map(r -> new Choix(String.valueOf(r[0]), (String) r[1])).toList());
    }

    /** Connexion reussie a l'application. */
    public void connexion(UtilisateurPrincipal u) {
        EcritureJournal.Contexte c = EcritureJournal.contexte();
        ecriture.ecrire(new EcritureJournal.Evenement(u.getEntrepriseId(), Instant.now(), u.getId(), u.getNomComplet(), "CONNEXION",
                "CONNEXION", "Utilisateur", "Utilisateur", u.getId(), "Connexion de " + u.getNomComplet(), null, null, c.adresseIp()));
    }

    /** Tentative de connexion refusee (mauvais mot de passe, compte desactive...). */
    public void echecConnexion(Long entrepriseId, Long utilisateurId, String nom, String motif) {
        EcritureJournal.Contexte c = EcritureJournal.contexte();
        ecriture.ecrire(new EcritureJournal.Evenement(entrepriseId, Instant.now(), utilisateurId, nom, "CONNEXION",
                "ECHEC_CONNEXION", "Utilisateur", "Utilisateur", utilisateurId,
                "Connexion refusee pour " + nom + " (" + motif + ")", null, null, c.adresseIp()));
    }

    private LigneDto versDto(JournalActivite j) {
        Map<String, String> avant = lire(j.getAvant());
        Map<String, String> apres = lire(j.getApres());
        List<Changement> changements = new ArrayList<>();
        Map<String, Boolean> champs = new LinkedHashMap<>();
        avant.keySet().forEach(k -> champs.put(k, true));
        apres.keySet().forEach(k -> champs.put(k, true));
        for (String champ : champs.keySet()) {
            changements.add(new Changement(champ, EcouteurAudit.champLisible(champ), avant.get(champ), apres.get(champ)));
        }
        return new LigneDto(j.getId(), j.getDateAction(), j.getAuteurId(), j.getAuteurNom(), j.getModule(), j.getAction(),
                j.getObjet(), j.getObjetLibelle(), j.getObjetId(), j.getDescription(), j.getAdresseIp(), changements);
    }

    private Map<String, String> lire(String texte) {
        if (texte == null || texte.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(texte, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
