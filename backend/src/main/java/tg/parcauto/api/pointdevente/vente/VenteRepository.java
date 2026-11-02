package tg.parcauto.api.pointdevente.vente;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Ventes et agregats de caisse. Les requetes JPQL sont cloisonnees par
 * Hibernate : chaque total ne porte que sur l'entreprise courante.
 */
public interface VenteRepository extends JpaRepository<Vente, Long> {

    @EntityGraph(attributePaths = {"lignes", "session"})
    @Query("select v from Vente v where v.id = :id")
    Optional<Vente> detail(@Param("id") Long id);

    @Query("""
            select v from Vente v
            where v.dateVente >= :debut and v.dateVente < :fin
            order by v.dateVente desc
            """)
    Page<Vente> periode(@Param("debut") Instant debut, @Param("fin") Instant fin, Pageable pageable);

    /** Ventes d'une periode, en ligne (Marketplace) ou au comptoir. */
    @Query("""
            select v from Vente v
            where v.dateVente >= :debut and v.dateVente < :fin and v.enLigne = :enLigne
            order by v.dateVente desc
            """)
    Page<Vente> periodeParCanal(@Param("debut") Instant debut, @Param("fin") Instant fin,
                                @Param("enLigne") boolean enLigne, Pageable pageable);

    Page<Vente> findBySessionIdOrderByDateVenteDesc(Long sessionId, Pageable pageable);

    /** [mode, total, nombre] des ventes validees d'une session. */
    @Query("""
            select v.modePaiement, coalesce(sum(v.total), 0), count(v) from Vente v
            where v.session.id = :sessionId and v.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE
            group by v.modePaiement
            """)
    List<Object[]> totauxSession(@Param("sessionId") Long sessionId);

    @Query("""
            select count(v) from Vente v
            where v.session.id = :sessionId and v.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.ANNULEE
            """)
    long annuleesSession(@Param("sessionId") Long sessionId);

    /** [produit, quantite, total, commandes] des ventes validees d'une session, du plus gros chiffre au plus petit. */
    @Query("""
            select l.libelle, sum(l.quantite), sum(l.total), count(distinct v.id) from LigneVente l join l.vente v
            where v.session.id = :sessionId and v.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE
            group by l.libelle order by sum(l.total) desc
            """)
    List<Object[]> parProduitSession(@Param("sessionId") Long sessionId);

    /** [vendeur, total, nombre] des ventes validees d'une session, du plus gros chiffre au plus petit. */
    @Query("""
            select v.caissierNom, sum(v.total), count(v) from Vente v
            where v.session.id = :sessionId and v.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE
            group by v.caissierNom order by sum(v.total) desc
            """)
    List<Object[]> parVendeurSession(@Param("sessionId") Long sessionId);

    /** [mode, total, nombre] des ventes validees sur une periode. */
    @Query("""
            select v.modePaiement, coalesce(sum(v.total), 0), count(v) from Vente v
            where v.dateVente >= :debut and v.dateVente < :fin
              and v.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE
            group by v.modePaiement
            """)
    List<Object[]> totauxPeriode(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /** [total, nombre] des ventes validees encaissees pour des commandes en ligne, sur une periode. */
    @Query("""
            select coalesce(sum(v.total), 0), count(v) from Vente v
            where v.dateVente >= :debut and v.dateVente < :fin and v.enLigne = true
              and v.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE
            """)
    List<Object[]> totauxEnLigne(@Param("debut") Instant debut, @Param("fin") Instant fin);

    @Query("""
            select count(v) from Vente v
            where v.dateVente >= :debut and v.dateVente < :fin
              and v.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.ANNULEE
            """)
    long annuleesPeriode(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /** [articleId, libelle, quantite, chiffre d'affaires, cout d'achat] des articles les plus vendus. */
    @Query("""
            select l.article.id, l.libelle, sum(l.quantite), sum(l.total),
                   coalesce(sum(l.quantite * coalesce(l.prixAchat, 0)), 0) from LigneVente l
            where l.vente.dateVente >= :debut and l.vente.dateVente < :fin
              and l.vente.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE
            group by l.article.id, l.libelle
            order by sum(l.total) desc
            """)
    List<Object[]> meilleursArticles(@Param("debut") Instant debut, @Param("fin") Instant fin, Pageable pageable);

    /** [date de vente, total] des ventes validees, agregees par jour cote service. */
    @Query("""
            select v.dateVente, v.total from Vente v
            where v.dateVente >= :debut and v.dateVente < :fin
              and v.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE
            """)
    List<Object[]> montantsPeriode(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /**
     * [date de vente, total, cout d'achat] de chaque ticket valide de la periode :
     * le service en tire l'evolution (par jour ou par mois) et l'affluence par heure.
     */
    @Query("""
            select v.dateVente, v.total, coalesce(sum(l.quantite * coalesce(l.prixAchat, 0)), 0)
            from Vente v left join v.lignes l
            where v.dateVente >= :debut and v.dateVente < :fin
              and v.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE
            group by v.id, v.dateVente, v.total
            """)
    List<Object[]> ticketsAvecCout(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /** [categorie, couleur, icone, chiffre d'affaires] : la repartition des ventes par categorie. */
    @Query("""
            select c.nom, c.couleur, c.icone, sum(l.total) from LigneVente l
            left join l.article a left join a.categorie c
            where l.vente.dateVente >= :debut and l.vente.dateVente < :fin
              and l.vente.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE
            group by c.nom, c.couleur, c.icone
            order by sum(l.total) desc
            """)
    List<Object[]> ventesParCategorie(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /** [code, produit, categorie, quantite, chiffre d'affaires, cout d'achat] par produit vendu. */
    @Query("""
            select a.code, a.nom, c.nom, sum(l.quantite), sum(l.total),
                   coalesce(sum(l.quantite * coalesce(l.prixAchat, 0)), 0)
            from LigneVente l join l.article a left join a.categorie c
            where l.vente.dateVente >= :debut and l.vente.dateVente < :fin
              and l.vente.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE
            group by a.code, a.nom, c.nom
            order by sum(l.total) desc
            """)
    List<Object[]> ventesParProduit(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /** [categorie, quantite, chiffre d'affaires, cout d'achat] par categorie. */
    @Query("""
            select c.nom, sum(l.quantite), sum(l.total), coalesce(sum(l.quantite * coalesce(l.prixAchat, 0)), 0)
            from LigneVente l left join l.article a left join a.categorie c
            where l.vente.dateVente >= :debut and l.vente.dateVente < :fin
              and l.vente.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE
            group by c.nom
            order by sum(l.total) desc
            """)
    List<Object[]> ventesCategoriesAvecCout(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /** [vendeur, commandes validees, chiffre d'affaires, commandes annulees] par caissier ou serveur. */
    @Query("""
            select v.caissierNom,
                   sum(case when v.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE then 1 else 0 end),
                   coalesce(sum(case when v.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE then v.total else 0 end), 0),
                   sum(case when v.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.ANNULEE then 1 else 0 end)
            from Vente v
            where v.dateVente >= :debut and v.dateVente < :fin
            group by v.caissierNom
            order by 3 desc
            """)
    List<Object[]> ventesParVendeur(@Param("debut") Instant debut, @Param("fin") Instant fin);

    /** Cout d'achat des articles vendus, pour la marge brute. */
    @Query("""
            select coalesce(sum(l.quantite * coalesce(l.prixAchat, 0)), 0) from LigneVente l
            where l.vente.dateVente >= :debut and l.vente.dateVente < :fin
              and l.vente.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE
            """)
    java.math.BigDecimal coutAchatPeriode(@Param("debut") Instant debut, @Param("fin") Instant fin);
}
