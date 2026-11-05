package tg.parcauto.api.pointdevente.stock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockServiceTest {

    private StockService service;
    private ArticleRepository articles;
    private Article article;

    @BeforeEach
    void preparer() {
        MouvementStockRepository mouvements = mock(MouvementStockRepository.class);
        when(mouvements.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        articles = mock(ArticleRepository.class);
        service = new StockService(articles, mouvements, org.mockito.Mockito.mock(tg.parcauto.api.pointdevente.lieu.LieuStockService.class));
        article = new Article();
        article.setNom("Savon");
        article.setStockActuel(new BigDecimal("10"));
    }

    @Test
    @DisplayName("Une vente diminue le stock et trace une quantite negative")
    void venteDiminueLeStock() {
        MouvementStock m = service.appliquer(article, TypeMouvementStock.VENTE, new BigDecimal("3"), null, null, 1L);

        assertThat(article.getStockActuel()).isEqualByComparingTo("7");
        assertThat(m.getQuantite()).isEqualByComparingTo("-3");
        assertThat(m.getStockApres()).isEqualByComparingTo("7");
    }

    @Test
    @DisplayName("On ne vend pas plus que le stock disponible")
    void stockInsuffisant() {
        assertThatThrownBy(() ->
                service.appliquer(article, TypeMouvementStock.VENTE, new BigDecimal("11"), null, null, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Stock insuffisant");
        assertThat(article.getStockActuel()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Un inventaire remplace le stock et enregistre l'ecart constate")
    void inventaire() {
        MouvementStock m = service.appliquer(article, TypeMouvementStock.INVENTAIRE, new BigDecimal("8"),
                null, "Comptage", null);

        assertThat(article.getStockActuel()).isEqualByComparingTo("8");
        assertThat(m.getQuantite()).isEqualByComparingTo("-2");
    }

    @Test
    @DisplayName("L'alerte se declenche au seuil, seulement pour un stock suivi")
    void alerteDeStock() {
        article.setSeuilAlerte(new BigDecimal("10"));
        assertThat(article.stockBas()).isTrue();
        article.setSuiviStock(false);
        assertThat(article.stockBas()).isFalse();
    }

    @Test
    @DisplayName("Une feuille d'inventaire ne trace que les articles dont le compte differe")
    void feuilleInventaire() {
        article.setId(1L);
        article.setSuiviStock(true);
        Article juste = new Article();
        juste.setId(2L);
        juste.setNom("Riz");
        juste.setSuiviStock(true);
        juste.setStockActuel(new BigDecimal("5"));
        when(articles.findById(1L)).thenReturn(java.util.Optional.of(article));
        when(articles.findById(2L)).thenReturn(java.util.Optional.of(juste));

        StockService.BilanInventaire bilan = service.inventaire(new StockService.Inventaire(java.util.List.of(
                new StockService.Comptage(1L, new BigDecimal("7")),
                new StockService.Comptage(2L, new BigDecimal("5"))), "Inventaire d'octobre"));

        assertThat(bilan.comptes()).isEqualTo(2);
        assertThat(bilan.corriges()).isEqualTo(1);
        assertThat(article.getStockActuel()).isEqualByComparingTo("7");
        assertThat(bilan.ecarts().get(0).quantite()).isEqualByComparingTo("-3");
    }
}
