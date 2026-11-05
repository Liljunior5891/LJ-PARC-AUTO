package tg.parcauto.api.pointdevente.lieu;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockLieuRepository extends JpaRepository<StockLieu, Long> {

    Optional<StockLieu> findByLieuIdAndArticleId(Long lieuId, Long articleId);

    @Query("select s from StockLieu s join fetch s.lieu l where s.article.id in :articles and l.actif = true")
    List<StockLieu> desArticles(@Param("articles") Collection<Long> articles);

    @Query("""
            select coalesce(sum(s.stock - s.reserve), 0) from StockLieu s
            where s.article.id = :article and s.lieu.visibleEnLigne = true and s.lieu.actif = true
            """)
    BigDecimal disponibleEnLigne(@Param("article") Long article);

    @Query("""
            select s from StockLieu s join fetch s.lieu l
            where s.article.id = :article and l.visibleEnLigne = true and l.actif = true
            order by l.principal desc, l.nom
            """)
    List<StockLieu> enLigne(@Param("article") Long article);

    @Query(value = """
            select s from StockLieu s join fetch s.article a
            where s.lieu.id = :lieu and a.actif = true and a.suiviStock = true
              and (:q = '' or lower(a.nom) like concat('%', :q, '%') or lower(a.code) like concat('%', :q, '%'))
            order by a.nom
            """, countQuery = """
            select count(s) from StockLieu s join s.article a
            where s.lieu.id = :lieu and a.actif = true and a.suiviStock = true
              and (:q = '' or lower(a.nom) like concat('%', :q, '%') or lower(a.code) like concat('%', :q, '%'))
            """)
    Page<StockLieu> duLieu(@Param("lieu") Long lieu, @Param("q") String q, Pageable pageable);

    /** Lieu encore charge : on ne le ferme pas tant qu'il garde de la marchandise. */
    @Query("select count(s) from StockLieu s where s.lieu.id = :lieu and (s.stock <> 0 or s.reserve <> 0)")
    long lignesNonVides(@Param("lieu") Long lieu);

    /** Lieu, references, quantite, reserve et valeur (au cout d'achat, au prix de vente) de chaque lieu. */
    @Query("""
            select s.lieu.id, count(s), coalesce(sum(s.stock), 0), coalesce(sum(s.reserve), 0),
                   coalesce(sum(s.stock * coalesce(s.article.prixAchat, 0)), 0),
                   coalesce(sum(s.stock * s.article.prixVente), 0)
            from StockLieu s where s.stock <> 0 or s.reserve <> 0 group by s.lieu.id
            """)
    List<Object[]> valeurParLieu();
}
