package tg.parcauto.api.pointdevente.catalogue;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Un produit epuise est en rupture, pas « sous le seuil » : les deux etats ne se recouvrent pas. */
class ArticleTest {

    private static Article article(String stock, String seuil) {
        Article a = new Article();
        a.setSuiviStock(true);
        a.setStockActuel(new BigDecimal(stock));
        a.setSeuilAlerte(seuil == null ? null : new BigDecimal(seuil));
        return a;
    }

    @Test
    void sousLeSeuilSeulementTantQuIlResteDuStock() {
        assertThat(article("3", "5").stockBas()).isTrue();
        assertThat(article("5", "5").stockBas()).isTrue();
        assertThat(article("6", "5").stockBas()).isFalse();
        assertThat(article("0", "5").stockBas()).isFalse();
        assertThat(article("-1", "5").stockBas()).isFalse();
        assertThat(article("2", null).stockBas()).isFalse();
    }
}
