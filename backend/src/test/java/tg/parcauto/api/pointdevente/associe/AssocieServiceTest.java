package tg.parcauto.api.pointdevente.associe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.Categorie;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssocieServiceTest {

    @Test
    @DisplayName("Sur un produit, le rattachement direct d'un associe l'emporte sur sa categorie")
    void produitAvantCategorie() {
        Categorie boissons = new Categorie();
        boissons.setId(1L);
        Article biere = new Article();
        biere.setId(10L);
        biere.setCategorie(boissons);

        AssociePart koffiCategorie = part(100L, null, boissons, "20");
        AssociePart koffiProduit = part(100L, biere, null, "35");
        AssociePart amaCategorie = part(200L, null, boissons, "10");

        Map<Long, AssociePart> taux = AssocieService.tauxEffectifs(List.of(koffiCategorie, koffiProduit, amaCategorie), biere);

        assertThat(taux.get(100L).getTaux()).isEqualByComparingTo("35");
        assertThat(taux.get(200L).getTaux()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Un produit hors des categories et produits d'un associe ne lui rapporte rien")
    void horsRattachement() {
        Categorie quincaillerie = new Categorie();
        quincaillerie.setId(2L);
        Article clou = new Article();
        clou.setId(11L);
        clou.setCategorie(quincaillerie);
        Categorie boissons = new Categorie();
        boissons.setId(1L);

        assertThat(AssocieService.tauxEffectifs(List.of(part(100L, null, boissons, "20")), clou)).isEmpty();
    }

    private static AssociePart part(Long associe, Article article, Categorie categorie, String taux) {
        AssociePart p = new AssociePart();
        p.setAssocieId(associe);
        p.setArticle(article);
        p.setCategorie(categorie);
        p.setTaux(new BigDecimal(taux));
        return p;
    }
}
