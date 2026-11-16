package tg.parcauto.api.common.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Correctifs de l'audit de securite du 2026-11-14 (WhatsApp). */
class SecuriteNotificationsTest {

    @Test
    void leNomSaisiParUnClientPublicPerdSesLiensEtSaMiseEnForme() {
        String t = VeilleAlertes.saisiePublique("*Nova* : compte bloque\nhttps://evil.example/x payez sur paye-ici.com/n");
        assertThat(t).doesNotContain("http").doesNotContain("paye-ici.com").doesNotContain("*").doesNotContain("\n");
        assertThat(t.length()).isLessThanOrEqualTo(47);
        assertThat(VeilleAlertes.saisiePublique("Kossi Mensah")).isEqualTo("« Kossi Mensah »");
        assertThat(VeilleAlertes.saisiePublique(null)).isEqualTo("client");
        assertThat(VeilleAlertes.saisiePublique("www.x.tg")).isEqualTo("client");
    }

    @Test
    void laCleWahaEstChiffreeEtRelue() {
        CoffreCleWhatsApp coffre = new CoffreCleWhatsApp("secret-de-test-assez-long-pour-deriver-une-cle");
        String stocke = coffre.chiffrer("cle-waha-123");
        assertThat(stocke).startsWith("v1:").doesNotContain("cle-waha-123");
        assertThat(coffre.estChiffree(stocke)).isTrue();
        assertThat(coffre.dechiffrer(stocke)).isEqualTo("cle-waha-123");
        // Ancienne valeur en clair : encore lisible, chiffree au prochain demarrage.
        assertThat(coffre.dechiffrer("ancienne-cle")).isEqualTo("ancienne-cle");
        // Autre cle de chiffrement : illisible plutot que fausse.
        assertThat(new CoffreCleWhatsApp("un-autre-secret-tout-aussi-long-que-le-premier").dechiffrer(stocke)).isNull();
    }
}
