package tg.parcauto.api.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerrouEcranServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final VerrouEcranService service = new VerrouEcranService(jdbc);
    private Instant maintenant = Instant.parse("2026-11-12T10:00:00Z");

    @BeforeEach
    void preparer() {
        when(jdbc.queryForList(anyString(), eq(Integer.class), any())).thenReturn(List.of(5));
        regler();
    }

    private void regler() {
        service.horloge(Clock.fixed(maintenant, ZoneOffset.UTC));
    }

    private void avancer(Duration d) {
        maintenant = maintenant.plus(d);
        regler();
    }

    private static UtilisateurPrincipal compte(Role... roles) {
        Utilisateur u = new Utilisateur();
        u.setId(7L);
        u.setEmail("c@test.tg");
        u.setNom("c");
        u.setMotDePasse("x");
        u.setRoles(EnumSet.of(roles[0], roles));
        u.setActif(true);
        u.setEntrepriseId(1L);
        return new UtilisateurPrincipal(u, "Test", true);
    }

    @Test
    void seulsLesPostesDeCaisseSontConcernes() {
        assertThat(service.concerne(compte(Role.CAISSIER))).isTrue();
        assertThat(service.concerne(compte(Role.SERVEUR))).isTrue();
        assertThat(service.concerne(compte(Role.CAISSIER_AGENCE))).isTrue();
        assertThat(service.concerne(compte(Role.PREPARATEUR))).isFalse();
        assertThat(service.concerne(compte(Role.CAISSIER, Role.GERANT))).isFalse();
        assertThat(service.concerne(compte(Role.CAISSIER_AGENCE, Role.SUPERVISEUR_AGENCE))).isFalse();
        assertThat(service.concerne(compte(Role.ADMIN))).isFalse();
    }

    @Test
    void nouvelleSessionVerrouilleeJusquAuCodePin() {
        UtilisateurPrincipal c = compte(Role.CAISSIER);
        assertThat(service.verrouille(c, "s1")).isTrue();
        assertThat(service.verrouille(c, "s1")).isTrue();
        service.deverrouiller("s1");
        assertThat(service.verrouille(c, "s1")).isFalse();
        // Une autre session du meme compte reste verrouillee.
        assertThat(service.verrouille(c, "s2")).isTrue();
    }

    @Test
    void verrouillageDemandeParLEcran() {
        UtilisateurPrincipal c = compte(Role.CAISSIER_AGENCE);
        service.deverrouiller("s1");
        service.verrouiller("s1");
        assertThat(service.verrouille(c, "s1")).isTrue();
    }

    @Test
    void inactiviteAuDelaDuDelaiEtDeLaMarge() {
        UtilisateurPrincipal c = compte(Role.SERVEUR);
        service.deverrouiller("s1");
        avancer(Duration.ofMinutes(6));
        assertThat(service.verrouille(c, "s1")).isFalse();
        avancer(Duration.ofMinutes(6));
        assertThat(service.verrouille(c, "s1")).isFalse();
        avancer(Duration.ofMinutes(7).plusSeconds(1));
        assertThat(service.verrouille(c, "s1")).isTrue();
        // Plus d'activite : il reste verrouille.
        assertThat(service.verrouille(c, "s1")).isTrue();
    }

    @Test
    void cleDeSecoursSansIdentifiantDeSession() {
        assertThat(VerrouEcranService.cle("abc", 1L, null)).isEqualTo("abc");
        assertThat(VerrouEcranService.cle(null, 1L, new java.util.Date(5000))).isEqualTo("u1:5000");
    }
}
