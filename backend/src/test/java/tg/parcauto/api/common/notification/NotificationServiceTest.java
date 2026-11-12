package tg.parcauto.api.common.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceTest {

    @Test
    void lesDestinatairesSontVerifiesEtNormalises() {
        assertThat(NotificationService.normaliser(NotificationService.Canal.EMAIL, " ama@exemple.tg "))
                .isEqualTo("ama@exemple.tg");
        assertThat(NotificationService.normaliser(NotificationService.Canal.EMAIL, "pas-une-adresse")).isNull();
        assertThat(NotificationService.normaliser(NotificationService.Canal.SMS, "90 12 34 56")).isEqualTo("+22890123456");
        assertThat(NotificationService.normaliser(NotificationService.Canal.SMS, "+228 90-12-34-56")).isEqualTo("+22890123456");
        assertThat(NotificationService.normaliser(NotificationService.Canal.WHATSAPP, "+33 6 12 34 56 78")).isEqualTo("+33612345678");
        assertThat(NotificationService.normaliser(NotificationService.Canal.SMS, "abc")).isNull();
        assertThat(NotificationService.normaliser(NotificationService.Canal.SMS, null)).isNull();
    }

    @Test
    void lesJournauxNeMontrentPasLeDestinataireEnEntier() {
        assertThat(NotificationService.masquer("ama@exemple.tg")).isEqualTo("a***@exemple.tg");
        assertThat(NotificationService.masquer("+22890123456")).isEqualTo("+228***56");
    }
}
