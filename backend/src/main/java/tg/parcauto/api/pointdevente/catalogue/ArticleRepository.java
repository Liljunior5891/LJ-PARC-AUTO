package tg.parcauto.api.pointdevente.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long>, JpaSpecificationExecutor<Article> {

    boolean existsByCodeIgnoreCase(String code);

    Optional<Article> findByCodeBarres(String codeBarres);

    Optional<Article> findByCodeIgnoreCase(String code);

    /** Articles proposes a la caisse, categorie chargee pour les boutons. */
    @Query("select a from Article a left join fetch a.categorie where a.actif = true order by a.nom")
    List<Article> catalogueCaisse();

    @Query("""
            select a from Article a left join fetch a.categorie
            where a.actif = true and a.suiviStock = true
              and a.seuilAlerte is not null and a.stockActuel - a.stockReserve <= a.seuilAlerte
            order by a.stockActuel - a.stockReserve
            """)
    List<Article> enStockBas();

    long countByActifTrue();

    /** Produits suivis en stock et epuises. */
    @org.springframework.data.jpa.repository.Query("select count(a) from Article a "
            + "where a.actif = true and a.suiviStock = true and a.stockActuel - a.stockReserve <= 0")
    long enRupture();
}
