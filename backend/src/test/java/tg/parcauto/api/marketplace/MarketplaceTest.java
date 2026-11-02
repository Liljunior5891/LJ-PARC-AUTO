package tg.parcauto.api.marketplace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Regles de la Marketplace qui ne dependent pas de la base. */
class MarketplaceTest {

    @Test
    @DisplayName("L'adresse proposee pour une boutique est tiree de son nom, sans accents ni espaces")
    void adresseDeBoutique() {
        assertThat(BoutiqueService.slugDe("Chez Ama — Épicerie & Bar")).isEqualTo("chez-ama-epicerie-bar");
        assertThat(BoutiqueService.slugDe("  LJ Transport SARL ")).isEqualTo("lj-transport-sarl");
        assertThat(BoutiqueService.slugDe("A")).isEqualTo("boutique");
        assertThat(BoutiqueService.slugDe(null)).isEqualTo("boutique");
        assertThat(BoutiqueService.slugDe("x".repeat(80))).hasSizeLessThanOrEqualTo(50);
    }

    @Test
    @DisplayName("Une location compte le jour de depart et le jour de retour")
    void dureeDeLocation() {
        LocalDate lundi = LocalDate.of(2026, 11, 2);
        assertThat(DemandeLocationService.jours(lundi, lundi)).isEqualTo(1);
        assertThat(DemandeLocationService.jours(lundi, lundi.plusDays(2))).isEqualTo(3);
    }

    @Test
    @DisplayName("Le jeton d'un lien de suivi a la forme attendue et change a chaque tirage")
    void jetonDeSuivi() {
        String premier = ClientEnLigneService.nouveauJeton();
        assertThat(premier).matches("^[a-f0-9]{32}$");
        assertThat(ClientEnLigneService.nouveauJeton()).isNotEqualTo(premier);
    }
}
