package tg.parcauto.api.plateforme.journal;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.audit.ChangementsJournal;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Journal d'audit general du super-admin : les journaux de toutes les entreprises
 * (modules, connexions et agences) et les actions de la plateforme elle-meme, en
 * une seule liste. Lecture seule, en SQL direct : le cloisonnement par entreprise
 * ne s'applique pas ici, l'acces est reserve au super-admin par le controleur.
 */
@Service
@RequiredArgsConstructor
public class JournalPlateformeService {

    private static final ZoneId FUSEAU = ZoneId.of("Africa/Lome");
    /** Valeur du filtre entreprise qui designe les actions de la plateforme (sans entreprise). */
    public static final String PLATEFORME = "plateforme";

    /**
     * Les deux journaux mis au meme format. Le journal des agences n'a ni adresse
     * IP ni libelle d'objet : le nom technique de l'objet en tient lieu.
     */
    private static final String UNION = """
            select 'M' || j.id as cle, j.entreprise_id, j.date_action, j.auteur_id, j.auteur_nom, j.module, j.action,
                   j.objet, j.objet_libelle, j.objet_id, j.description, j.avant, j.apres, j.adresse_ip
              from journal_activite j
            union all
            select 'A' || a.id, a.entreprise_id, a.date_action, a.auteur_id, a.auteur_nom, 'AGENCES', a.action,
                   a.objet, a.objet, a.objet_id, a.description, a.avant, a.apres, null
              from agc_journal_audit a
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public record Filtres(String entreprise, String module, String action, LocalDate du, LocalDate au, String recherche) {
    }

    public record Ligne(String id, Instant date, Long entrepriseId, String entrepriseNom, Long auteurId, String auteurNom,
                        String module, String action, String objet, String objetLibelle, Long objetId, String description,
                        String adresseIp, List<ChangementsJournal.Changement> changements) {
    }

    public record Compte(String valeur, String libelle, long nombre) {
    }

    public record Jour(LocalDate jour, long nombre) {
    }

    public record Synthese(long total, long creations, long modifications, long suppressions, long connexions,
                           long connexionsRefusees, long actionsPlateforme, long entreprisesActives,
                           List<Compte> parModule, List<Compte> parEntreprise, List<Compte> parAuteur, List<Jour> parJour) {
    }

    public record Choix(String valeur, String libelle) {
    }

    // ------------------------------------------------------------------ liste

    /**
     * Une page d'actions. Le total n'est compte que pour la premiere page : les suivantes
     * reprennent celui que l'ecran a deja recu ({@code totalConnu}), sans relire le journal.
     */
    public Page<Ligne> lister(Filtres f, int page, int taille, Long totalConnu) {
        int t = Math.min(Math.max(taille, 1), 100);
        int p = Math.max(page, 0);
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = conditions(f, params);
        long total = p > 0 && totalConnu != null && totalConnu >= 0 ? totalConnu
                : valeur(jdbc.queryForObject("select count(*) from (" + UNION + ") j" + where, params, Long.class));
        params.addValue("limite", t).addValue("decalage", (long) p * t);
        List<Ligne> lignes = jdbc.query("""
                select j.*, e.nom as entreprise_nom from (%s) j
                  left join entreprise e on e.id = j.entreprise_id
                %s
                 order by j.date_action desc, j.cle desc
                 limit :limite offset :decalage
                """.formatted(UNION, where), params, (rs, i) -> {
            Timestamp date = rs.getTimestamp("date_action");
            long entreprise = rs.getLong("entreprise_id");
            boolean sansEntreprise = rs.wasNull();
            long auteur = rs.getLong("auteur_id");
            boolean sansAuteur = rs.wasNull();
            long objet = rs.getLong("objet_id");
            boolean sansObjet = rs.wasNull();
            String action = rs.getString("action");
            return new Ligne(rs.getString("cle"), date == null ? null : date.toInstant(),
                    sansEntreprise ? null : entreprise, rs.getString("entreprise_nom"),
                    sansAuteur ? null : auteur, rs.getString("auteur_nom"), rs.getString("module"), action,
                    rs.getString("objet"), rs.getString("objet_libelle"), sansObjet ? null : objet,
                    rs.getString("description"), rs.getString("adresse_ip"),
                    ChangementsJournal.lire(json, rs.getString("avant"), rs.getString("apres")));
        });
        return new PageImpl<>(lignes, PageRequest.of(p, t), total);
    }

    // --------------------------------------------------------------- synthese

    /**
     * Rapport de la periode, aux memes filtres que la liste : volumes par action, par module,
     * entreprises et auteurs les plus actifs, activite par jour. Une seule lecture du journal
     * (GROUPING SETS) produit tous les regroupements.
     */
    public Synthese synthese(Filtres f) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = conditions(f, params);
        record Groupe(String module, Long entreprise, String auteur, LocalDate jour, int niveau, long nombre,
                      long creations, long modifications, long suppressions, long connexions, long refusees,
                      long plateforme, long entreprises) {
        }
        List<Groupe> groupes = jdbc.query("""
                select j.module, j.entreprise_id, coalesce(j.auteur_nom, 'Systeme') as auteur,
                       cast(j.date_action at time zone 'Africa/Lome' as date) as jour,
                       grouping(j.module, j.entreprise_id, coalesce(j.auteur_nom, 'Systeme'),
                                cast(j.date_action at time zone 'Africa/Lome' as date)) as niveau,
                       count(*) as n,
                       count(*) filter (where j.action = 'CREATION') as creations,
                       count(*) filter (where j.action = 'MODIFICATION') as modifications,
                       count(*) filter (where j.action = 'SUPPRESSION') as suppressions,
                       count(*) filter (where j.action = 'CONNEXION') as connexions,
                       count(*) filter (where j.action = 'ECHEC_CONNEXION') as refusees,
                       count(*) filter (where j.entreprise_id is null) as plateforme,
                       count(distinct j.entreprise_id) as entreprises
                  from (%s) j %s
                 group by grouping sets ((j.module), (j.entreprise_id), (coalesce(j.auteur_nom, 'Systeme')),
                                         (cast(j.date_action at time zone 'Africa/Lome' as date)), ())
                """.formatted(UNION, where), params, (rs, i) -> {
            long entreprise = rs.getLong("entreprise_id");
            boolean sansEntreprise = rs.wasNull();
            java.sql.Date jour = rs.getDate("jour");
            return new Groupe(rs.getString("module"), sansEntreprise ? null : entreprise, rs.getString("auteur"),
                    jour == null ? null : jour.toLocalDate(), rs.getInt("niveau"), rs.getLong("n"),
                    rs.getLong("creations"), rs.getLong("modifications"), rs.getLong("suppressions"),
                    rs.getLong("connexions"), rs.getLong("refusees"), rs.getLong("plateforme"), rs.getLong("entreprises"));
        });

        // grouping() vaut 0 pour la colonne regroupee : 0111 module, 1011 entreprise, 1101 auteur, 1110 jour, 1111 total.
        Groupe total = groupes.stream().filter(g -> g.niveau() == 0b1111).findFirst()
                .orElse(new Groupe(null, null, null, null, 0b1111, 0, 0, 0, 0, 0, 0, 0, 0));
        Map<Long, String> noms = new java.util.HashMap<>();
        jdbc.query("select id, nom from entreprise", rs -> { noms.put(rs.getLong("id"), rs.getString("nom")); });
        java.util.Comparator<Compte> parNombre = java.util.Comparator.comparingLong(Compte::nombre).reversed();

        List<Compte> parModule = groupes.stream().filter(g -> g.niveau() == 0b0111)
                .map(g -> new Compte(g.module(), g.module(), g.nombre())).sorted(parNombre).toList();
        List<Compte> parEntreprise = groupes.stream().filter(g -> g.niveau() == 0b1011)
                .map(g -> g.entreprise() == null ? new Compte(PLATEFORME, "Plateforme", g.nombre())
                        : new Compte(String.valueOf(g.entreprise()), noms.getOrDefault(g.entreprise(), "Entreprise " + g.entreprise()), g.nombre()))
                .sorted(parNombre).limit(8).toList();
        List<Compte> parAuteur = groupes.stream().filter(g -> g.niveau() == 0b1101)
                .map(g -> new Compte(g.auteur(), g.auteur(), g.nombre())).sorted(parNombre).limit(8).toList();
        List<Jour> parJour = groupes.stream().filter(g -> g.niveau() == 0b1110)
                .map(g -> new Jour(g.jour(), g.nombre())).sorted(java.util.Comparator.comparing(Jour::jour)).toList();

        return new Synthese(total.nombre(), total.creations(), total.modifications(), total.suppressions(),
                total.connexions(), total.refusees(), total.plateforme(), total.entreprises(),
                parModule, parEntreprise, parAuteur, parJour);
    }

    /** Entreprises, pour le filtre. */
    public List<Choix> entreprises() {
        return jdbc.query("select id, nom from entreprise order by lower(nom)",
                (rs, i) -> new Choix(String.valueOf(rs.getLong("id")), rs.getString("nom")));
    }

    // ------------------------------------------------------------------ outils

    private String conditions(Filtres f, MapSqlParameterSource params) {
        List<String> c = new ArrayList<>();
        if (f.du() != null && f.au() != null && f.du().isAfter(f.au())) {
            throw new BusinessException("La date de debut est posterieure a la date de fin");
        }
        if (f.entreprise() != null && !f.entreprise().isBlank()) {
            if (PLATEFORME.equals(f.entreprise())) {
                c.add("j.entreprise_id is null");
            } else {
                try {
                    params.addValue("entreprise", Long.valueOf(f.entreprise()));
                } catch (NumberFormatException e) {
                    throw new BusinessException("Entreprise inconnue");
                }
                c.add("j.entreprise_id = :entreprise");
            }
        }
        if (f.module() != null && !f.module().isBlank()) {
            params.addValue("module", f.module());
            c.add("j.module = :module");
        }
        if (f.action() != null && !f.action().isBlank()) {
            params.addValue("action", f.action());
            c.add("j.action = :action");
        }
        if (f.du() != null) {
            params.addValue("du", Timestamp.from(f.du().atStartOfDay(FUSEAU).toInstant()));
            c.add("j.date_action >= :du");
        }
        if (f.au() != null) {
            params.addValue("au", Timestamp.from(f.au().plusDays(1).atStartOfDay(FUSEAU).toInstant()));
            c.add("j.date_action < :au");
        }
        if (f.recherche() != null && !f.recherche().isBlank()) {
            String motif = "%" + f.recherche().trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            params.addValue("motif", motif);
            c.add("(lower(j.description) like :motif or lower(coalesce(j.auteur_nom, '')) like :motif)");
        }
        return c.isEmpty() ? "" : " where " + String.join(" and ", c);
    }

    private static long valeur(Long n) {
        return n == null ? 0 : n;
    }
}
