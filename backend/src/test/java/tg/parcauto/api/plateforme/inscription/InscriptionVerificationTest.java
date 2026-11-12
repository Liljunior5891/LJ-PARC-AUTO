package tg.parcauto.api.plateforme.inscription;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.notification.NotificationService;
import tg.parcauto.api.parametrage.SocieteRepository;
import tg.parcauto.api.plateforme.abonnement.AbonnementService;
import tg.parcauto.api.plateforme.entreprise.EntrepriseRepository;
import tg.parcauto.api.plateforme.module.ModuleService;
import tg.parcauto.api.security.UtilisateurRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InscriptionVerificationTest {

    private UtilisateurRepository utilisateurs;
    private JdbcTemplate jdbc;
    private NotificationService notifications;
    private InscriptionService service;

    private final InscriptionService.Demande demande = new InscriptionService.Demande(
            "Boutique Ama", "Togo", "90123456", "Ama", null, "ama@exemple.tg", "MotDePasse2026!", List.of(), null);

    @BeforeEach
    void preparer() {
        utilisateurs = mock(UtilisateurRepository.class);
        jdbc = mock(JdbcTemplate.class);
        notifications = mock(NotificationService.class);
        service = new InscriptionService(mock(EntrepriseRepository.class), utilisateurs, mock(SocieteRepository.class),
                mock(ModuleService.class), mock(AbonnementService.class), new BCryptPasswordEncoder(),
                mock(TransactionTemplate.class), jdbc, notifications, new ObjectMapper());
        ReflectionTestUtils.setField(service, "urlApplication", "https://app.exemple.tg");
    }

    @Test
    void uneAdresseLibreRecoitUnLienEtLaDemandeEstGardeeSansLeMotDePasseEnClair() {
        when(utilisateurs.existsByEmailIgnoreCase("ama@exemple.tg")).thenReturn(false);

        service.demander(demande);

        ArgumentCaptor<String> donnees = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(anyString(), eq("ama@exemple.tg"), anyString(), donnees.capture(), any());
        assertThat(donnees.getValue()).doesNotContain("MotDePasse2026!").contains("$2a$");
        ArgumentCaptor<String> texte = ArgumentCaptor.forClass(String.class);
        verify(notifications).email(isNull(), eq("ama@exemple.tg"), anyString(), texte.capture(), eq("VERIFICATION_EMAIL"));
        assertThat(texte.getValue()).contains("https://app.exemple.tg/inscription/confirmation?jeton=");
    }

    @Test
    void uneAdresseDejaInscriteRecoitUnAvisEtRienNEstGarde() {
        when(utilisateurs.existsByEmailIgnoreCase("ama@exemple.tg")).thenReturn(true);

        service.demander(demande);

        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(notifications).email(isNull(), eq("ama@exemple.tg"), anyString(), anyString(), eq("INSCRIPTION_EXISTANTE"));
    }

    @Test
    void unLienMalFormeEstRefuseSansToucherALaBase() {
        assertThatThrownBy(() -> service.confirmer("abc")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.confirmer(null)).isInstanceOf(BusinessException.class);
        verify(jdbc, never()).queryForList(anyString(), any(Object[].class));
    }
}
