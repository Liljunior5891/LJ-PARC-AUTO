package tg.parcauto.api.marketplace;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Place de marche commune : toutes les boutiques publiees de la plateforme et
 * leurs offres, pour un visiteur qui ne connait encore aucune boutique.
 * <p>
 * Lecture transversale a toutes les entreprises, donc en SQL direct, hors du
 * cloisonnement d'Hibernate ; elle ne renvoie que des donnees publiques
 * (vitrine et offres publiees), jamais de commandes ni de clients. Seules les
 * entreprises actives qui ont le module Marketplace sont listees.
 */
@Service
@RequiredArgsConstructor
public class MarcheService {

    private static final int OFFRES_MAX = 60;

    /** Boutiques visibles : publiees, entreprise active, module Marketplace actif. */
    private static final String BOUTIQUES_VISIBLES = """
            from mkt_boutique b
            join entreprise e on e.id = b.entreprise_id and e.statut = 'ACTIVE'
            join entreprise_module em on em.entreprise_id = b.entreprise_id
                 and em.module = 'MARKETPLACE' and em.actif
            where b.publiee
            """;

    private final JdbcTemplate jdbc;

    public MarketplaceDto.Marche parcourir(String recherche, String categorie, String ville) {
        String q = recherche == null || recherche.isBlank() ? null : "%" + recherche.trim().toLowerCase() + "%";
        String cat = categorie == null || categorie.isBlank() ? null : categorie.trim();
        String vil = ville == null || ville.isBlank() ? null : ville.trim();

        List<Object> params = new ArrayList<>();
        StringBuilder filtreBoutique = new StringBuilder();
        if (cat != null) {
            filtreBoutique.append(" and b.categorie = ?");
            params.add(cat);
        }
        if (vil != null) {
            filtreBoutique.append(" and lower(b.ville) = lower(?)");
            params.add(vil);
        }

        // Boutiques : leur nom, slogan ou categorie correspond, ou l'une de leurs offres.
        List<Object> paramsBoutiques = new ArrayList<>(params);
        String filtreTexteBoutique = "";
        if (q != null) {
            filtreTexteBoutique = """
                     and (lower(b.nom) like ? or lower(coalesce(b.slogan, '')) like ?
                          or lower(coalesce(b.categorie, '')) like ?
                          or exists (select 1 from mkt_offre o2 where o2.entreprise_id = b.entreprise_id
                                     and o2.publiee and lower(o2.titre) like ?))
                    """;
            paramsBoutiques.addAll(List.of(q, q, q, q));
        }
        List<MarketplaceDto.BoutiqueMarche> boutiques = jdbc.query("""
                select b.slug, b.nom, b.slogan, b.categorie, b.ville, s.logo_base64, s.logo_type, b.couverture,
                       (select count(*) from mkt_offre o where o.entreprise_id = b.entreprise_id and o.publiee) offres
                """ + BOUTIQUES_VISIBLES.replace("from mkt_boutique b",
                        "from mkt_boutique b left join parametre_societe s on s.entreprise_id = b.entreprise_id")
                + filtreBoutique + filtreTexteBoutique + " order by offres desc, b.nom limit 48",
                (rs, i) -> new MarketplaceDto.BoutiqueMarche(rs.getString("slug"), rs.getString("nom"),
                        rs.getString("slogan"), rs.getString("categorie"), rs.getString("ville"),
                        logo(rs.getString("logo_base64"), rs.getString("logo_type")), rs.getString("couverture"),
                        rs.getLong("offres")),
                paramsBoutiques.toArray());

        // Offres : titre, prix et image repris de l'article ou du vehicule quand l'offre n'en porte pas.
        List<Object> paramsOffres = new ArrayList<>(params);
        String filtreTexteOffre = "";
        if (q != null) {
            filtreTexteOffre = " and (lower(o.titre) like ? or lower(coalesce(o.categorie, '')) like ?)";
            paramsOffres.addAll(List.of(q, q));
        }
        List<MarketplaceDto.OffreMarche> offres = jdbc.query("""
                select o.id, b.slug, b.nom boutique, b.ville, o.type, o.titre, o.categorie,
                       coalesce(o.prix, a.prix_vente) prix, o.unite_prix,
                       coalesce(o.image, a.image, c.photo_url) image
                """ + BOUTIQUES_VISIBLES.replace("where b.publiee", """
                        join mkt_offre o on o.entreprise_id = b.entreprise_id and o.publiee
                        left join pdv_article a on o.type = 'ARTICLE' and a.id = o.article_id
                        left join camion c on o.type = 'VEHICULE' and c.id = o.camion_id
                        where b.publiee
                          and (o.type <> 'ARTICLE' or (a.actif and (not a.suivi_stock or a.stock_actuel - a.stock_reserve > 0)))
                          and (o.type <> 'VEHICULE' or (c.actif and c.statut not in ('VENDU', 'HORS_SERVICE', 'ACCIDENTE')))
                          and (o.type <> 'LIBRE' or o.stock is null or o.stock > 0)
                        """)
                + filtreBoutique + filtreTexteOffre
                + " order by o.mise_en_avant desc, o.modifie_le desc nulls last limit " + OFFRES_MAX,
                (rs, i) -> new MarketplaceDto.OffreMarche(rs.getLong("id"), rs.getString("slug"),
                        rs.getString("boutique"), rs.getString("ville"), Offre.Type.valueOf(rs.getString("type")),
                        rs.getString("titre"), rs.getString("categorie"), rs.getBigDecimal("prix"),
                        rs.getString("unite_prix"), rs.getString("image")),
                paramsOffres.toArray());

        List<String> categories = jdbc.queryForList("select distinct b.categorie " + BOUTIQUES_VISIBLES
                + " and b.categorie is not null order by 1", String.class);
        List<String> villes = jdbc.queryForList("select distinct b.ville " + BOUTIQUES_VISIBLES
                + " and b.ville is not null order by 1", String.class);
        return new MarketplaceDto.Marche(boutiques, offres, categories, villes);
    }

    private static String logo(String base64, String type) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        return base64.startsWith("data:") ? base64 : "data:" + (type == null ? "image/png" : type) + ";base64," + base64;
    }
}
