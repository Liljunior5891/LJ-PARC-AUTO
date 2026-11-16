package tg.parcauto.api.pointdevente.horsligne;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import tg.parcauto.api.common.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;

/** Une erreur passagere laisse la vente sur l'appareil ; les autres en font un conflit. */
class ErreurPassagereTest {

    @Test
    void connexionVerrouEtVersionSontPassageres() {
        assertThat(HorsLignePdvService.passagere(new DataAccessResourceFailureException("connexion perdue"))).isTrue();
        assertThat(HorsLignePdvService.passagere(new CannotAcquireLockException("verrou"))).isTrue();
        assertThat(HorsLignePdvService.passagere(new ObjectOptimisticLockingFailureException("Article", 1L))).isTrue();
        assertThat(HorsLignePdvService.passagere(new RuntimeException("enveloppe",
                new java.sql.SQLTransientConnectionException("delai")))).isTrue();
    }

    @Test
    void regleMetierEtDoublonNeSontPasPassagers() {
        assertThat(HorsLignePdvService.passagere(new BusinessException("Session fermee"))).isFalse();
        assertThat(HorsLignePdvService.passagere(new DataIntegrityViolationException("cle unique"))).isFalse();
        assertThat(HorsLignePdvService.passagere(new IllegalStateException("bogue"))).isFalse();
    }
}
