package tg.parcauto.api.plateforme.abonnement.paygate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.LimiteurRequetes;
import tg.parcauto.api.plateforme.abonnement.AbonnementService;
import tg.parcauto.api.plateforme.abonnement.MoyenPaiementAbonnement;
import tg.parcauto.api.plateforme.abonnement.PaiementAbonnement;
import tg.parcauto.api.plateforme.abonnement.PaiementAbonnementRepository;
import tg.parcauto.api.plateforme.abonnement.Periodicite;
import tg.parcauto.api.plateforme.abonnement.StatutPaiementAbonnement;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaiementEnLigneServiceTest {

    private PayGateClient payGate;
    private AbonnementService abonnements;
    private PaiementAbonnementRepository paiements;
    private PaiementEnLigneService service;

    private final AbonnementService.DemandeEnLigne demande = new AbonnementService.DemandeEnLigne(
            1L, Periodicite.MENSUEL, List.of(), "TMONEY", "+228 90 12 34 56");

    @BeforeEach
    void preparer() {
        payGate = mock(PayGateClient.class);
        abonnements = mock(AbonnementService.class);
        paiements = mock(PaiementAbonnementRepository.class);
        service = new PaiementEnLigneService(payGate, abonnements, paiements, new LimiteurRequetes());
        when(payGate.actif()).thenReturn(true);
    }

    private PaiementAbonnement paiement() {
        PaiementAbonnement p = new PaiementAbonnement();
        p.setId(7L);
        p.setMontant(new BigDecimal("15000"));
        p.setFormuleNom("Pro");
        p.setPeriodicite(Periodicite.MENSUEL);
        p.setMoyen(MoyenPaiementAbonnement.PAYGATE);
        p.setStatut(StatutPaiementAbonnement.EN_ATTENTE);
        p.setPaygateIdentifiant("ABO0123456789ABCDEF01234567");
        return p;
    }

    @Test
    void leNumeroEstRameneAuxHuitChiffresLocaux() {
        assertThat(PaiementEnLigneService.telephone("+228 90 12 34 56")).isEqualTo("90123456");
        assertThat(PaiementEnLigneService.telephone("70-12-34-56")).isEqualTo("70123456");
        assertThatThrownBy(() -> PaiementEnLigneService.telephone("1234")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PaiementEnLigneService.telephone("50123456")).isInstanceOf(BusinessException.class);
    }

    @Test
    void laDemandeEstEnvoyeeAvecLeMontantDuDevisEtNotreIdentifiant() {
        PaiementAbonnement p = paiement();
        when(abonnements.preparerPaiementEnLigne(eq(3L), any(), eq("90123456"))).thenReturn(p);
        when(payGate.demanderPaiement(anyString(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(new PayGateClient.Demande(0, "TX1"));

        service.payer(3L, demande);

        verify(payGate).demanderPaiement(eq("90123456"), eq(15000L), eq(p.getPaygateIdentifiant()), eq("TMONEY"),
                anyString());
        verify(abonnements).demandeEnvoyee(7L, "TX1");
    }

    @Test
    void uneDemandeRefuseeParPayGateRejetteLePaiement() {
        when(abonnements.preparerPaiementEnLigne(eq(3L), any(), anyString())).thenReturn(paiement());
        when(payGate.demanderPaiement(anyString(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(new PayGateClient.Demande(4, null));

        assertThatThrownBy(() -> service.payer(3L, demande)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Numero ou reseau refuse");
        verify(abonnements).demandeRefusee(eq(7L), anyString());
        verify(abonnements, never()).demandeEnvoyee(anyLong(), anyString());
    }

    @Test
    void sansCleLePaiementEnLigneEstRefuse() {
        when(payGate.actif()).thenReturn(false);
        assertThatThrownBy(() -> service.payer(3L, demande)).isInstanceOf(BusinessException.class);
        verify(abonnements, never()).preparerPaiementEnLigne(anyLong(), any(), anyString());
    }

    @Test
    void unRappelAvecUnIdentifiantMalFormeEstIgnoreSansAppelerPayGate() {
        service.rappel("n'importe quoi");
        service.rappel(null);
        verify(payGate, never()).etat(anyString());
    }

    @Test
    void laVerificationApplique_lEtatRedemandeAPayGate() {
        PaiementAbonnement p = paiement();
        when(abonnements.paiementEnLigneDe(3L, 7L)).thenReturn(p);
        PayGateClient.Etat paye = new PayGateClient.Etat(0, "TX1", "REF-OP", "TMONEY");
        when(payGate.etat(p.getPaygateIdentifiant())).thenReturn(paye);

        service.verifier(3L, 7L);

        verify(abonnements).appliquerEtatPayGate(p.getPaygateIdentifiant(), paye);
    }
}
