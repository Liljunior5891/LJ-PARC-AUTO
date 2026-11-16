package tg.parcauto.api.common.notification;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VeilleAlertesTest {

    @Test
    void lesEvenementsSontRegroupesEnUnMessageCourt() {
        List<VeilleAlertes.Evenement> liste = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            liste.add(new VeilleAlertes.Evenement(1L, "k" + i, "Article " + i, "/pdv/stock"));
        }
        String texte = VeilleAlertes.texte(liste);
        assertThat(texte).startsWith("• Article 1\n• Article 2").contains("• Article 10").doesNotContain("Article 11")
                .endsWith("... et 2 autre(s)");
        assertThat(VeilleAlertes.lienCommun(liste)).isEqualTo("/pdv/stock");
        liste.add(new VeilleAlertes.Evenement(1L, "x", "Depense", "/depenses"));
        assertThat(VeilleAlertes.lienCommun(liste)).isNull();
    }

    @Test
    void lesMontantsEtEcartsSontLisibles() {
        assertThat(VeilleAlertes.entier(new BigDecimal("1500000.4"))).isEqualTo("1 500 000");
        assertThat(VeilleAlertes.ecart(new BigDecimal("-2500"))).isEqualTo("manquant de 2 500 F");
        assertThat(VeilleAlertes.ecart(new BigDecimal("300"))).isEqualTo("excedent de 300 F");
    }

    @Test
    void leNumeroDevientUnIdentifiantWhatsApp() {
        assertThat(WhatsAppWaha.chatId("+22890123456")).isEqualTo("22890123456@c.us");
    }

    @Test
    void chaqueTypeAUnLibelleEtUneDescription() {
        for (TypeAlerte t : TypeAlerte.values()) {
            assertThat(t.getLibelle()).isNotBlank();
            assertThat(t.getDescription()).isNotBlank();
            assertThat(t.name().length()).isLessThanOrEqualTo(40);
        }
    }
}
