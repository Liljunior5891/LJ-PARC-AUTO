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

    /** Ventes validees dont il reste a remettre, les plus anciennes d'abord ; {@code clientId} nul : tous. */
    @Query("""
            select distinct v from Vente v join v.lignes l
            where v.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE
              and l.quantiteLivree < l.quantite
              and (:clientId is null or v.client.id = :clientId)
              and (:q = '' or lower(v.numero) like concat('%', :q, '%')
                   or lower(coalesce(v.clientNom, '')) like concat('%', :q, '%'))
            order by v.dateVente
            """)
    Page<Vente> aLivrer(@Param("q") String q, @Param("clientId") Long clientId, Pageable pageable);

    /** Ventes qui ont un bon de livraison ou un solde, les plus recentes d'abord : leurs documents restent accessibles. */
    @Query("""
            select v from Vente v
            where (exists (select b.id from tg.parcauto.api.pointdevente.materiaux.BonLivraison b where b.vente = v)
                   or exists (select s.id from tg.parcauto.api.pointdevente.materiaux.SoldeReste s where s.vente = v))
              and (:clientId is null or v.client.id = :clientId)
              and (:q = '' or lower(v.numero) like concat('%', :q, '%')
                   or lower(coalesce(v.clientNom, '')) like concat('%', :q, '%'))
            order by v.dateVente desc
            """)
    Page<Vente> avecDocuments(@Param("q") String q, @Param("clientId") Long clientId, Pageable pageable);

    /**
     * [mode, encaisse, nombre] des ventes validees d'une session : ce qui est
     * entre dans la caisse a la vente (le total, ou l'acompte d'une vente a credit).
     */
    @Query("""
            select v.modePaiement, coalesce(sum(v.montantPaye), 0), count(v) from Vente v
            where v.session.id = :sessionId and v.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE
            group by v.modePaiement
            """)
    List<Object[]> totauxSession(@Param("sessionId") Long sessionId);

    /** [chiffre d'affaires, credit accorde] des ventes validees d'une session. */
    @Query("""
            select coalesce(sum(v.total), 0),
                   coalesce(sum(case when v.total > v.montantPaye then v.total - v.montantPaye else 0 end), 0) from Vente v
            where v.session.id = :sessionId and v.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE
            """)
    List<Object[]> chiffreSession(@Param("sessionId") Long sessionId);

    /** Ventes validees encore dues par un client, de la plus ancienne a la plus recente. */
    @Query("""
            select v from Vente v
            where v.client.id = :clientId and v.resteDu > 0
              and v.statut = tg.parcauto.api.pointdevente.vente.Vente.Statut.VALIDEE
            order by v.dateVente, v.id
            """)
    List<Vente> duesDuClient(@Param("clientId") Long clientId);

    /** Ventes d'un client, les plus recentes d'abord. */
    Page<Vente> findByClientIdOrderByDateVenteDesc(Long clientId, Pageable pageable);

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

    /** [code, produit, categorie, quantite (unite de base), chiffre d'affaires, cout d'achat] par produit vendu. */
    @Query("""
            select a.code, a.nom, c.nom, sum(l.quantiteBase), sum(l.total),
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
            select c.nom, sum(l.quantiteBase), sum(l.total), coalesce(sum(l.quantite * coalesce(l.prixAchat, 0)), 0)
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
