package tg.parcauto.api.pointdevente.materiaux;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.pointdevente.caisse.Caisse;
import tg.parcauto.api.pointdevente.caisse.CaisseService;
import tg.parcauto.api.pointdevente.caisse.MouvementCaisse;
import tg.parcauto.api.pointdevente.caisse.PerimetreCaisse;
import tg.parcauto.api.pointdevente.caisse.SessionCaisse;
import tg.parcauto.api.pointdevente.caisse.SessionTravailService;
import tg.parcauto.api.pointdevente.vente.ModePaiementPdv;
import tg.parcauto.api.pointdevente.vente.VenteRepository;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.Utilisateur;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

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

class DepotClientServiceTest {

    private DepotClientRepository repository;
    private VenteRepository ventes;
    private CaisseService caisses;
    private DepotClientService service;
    private SessionCaisse session;
    private final List<DepotClient> enregistres = new ArrayList<>();

    @BeforeEach
    void preparer() {
        repository = mock(DepotClientRepository.class);
        ventes = mock(VenteRepository.class);
        caisses = mock(CaisseService.class);
        ClientPdvRepository clients = mock(ClientPdvRepository.class);
        SessionTravailService sessions = mock(SessionTravailService.class);
        NumerotationService numerotation = mock(NumerotationService.class);
        when(numerotation.prochainNumero(anyString())).thenReturn("DEP-2026-0001");
        ClientPdv kodjo = new ClientPdv();
        kodjo.setId(4L);
        kodjo.setNom("Kodjo BTP");
        when(clients.verrouiller(4L)).thenReturn(Optional.of(kodjo));
        when(clients.findById(4L)).thenReturn(Optional.of(kodjo));
        Caisse comptoir = new Caisse();
        comptoir.setNom("Comptoir");
        session = new SessionCaisse();
        session.setId(3L);
        session.setNumero("CAI-1");
        session.setCaisse(comptoir);
        when(sessions.exiger(any(), any())).thenReturn(session);
        when(repository.save(any())).thenAnswer(i -> {
            enregistres.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(caisses.retraitRemise(anyLong(), any(), anyString(), any())).thenReturn(new MouvementCaisse());
        service = new DepotClientService(repository, clients, ventes, mock(ReglageMateriauxService.class), sessions,
                caisses, numerotation, mock(PerimetreCaisse.class), mock(ReglementClientRepository.class));
    }

    /** Solde : depots et remboursements nets, puis achats payes avec le depot. */
    private void solde(String netDepots, String achats) {
        when(repository.mouvementsNets(any())).thenReturn(Collections.singletonList(new Object[]{4L, new BigDecimal(netDepots)}));
        when(ventes.payesParDepot(any())).thenReturn(Collections.singletonList(new Object[]{4L, new BigDecimal(achats)}));
    }

    @Test
    @DisplayName("Le solde = depots - remboursements - achats payes avec le depot")
    void soldeDuDepot() {
        solde("100000", "35000");

        assertThat(service.solde(4L)).isEqualByComparingTo("65000");
    }

    @Test
    @DisplayName("Un depot entre sur la session du poste ; le recu donne la monnaie rendue")
    void depotEnEspeces() {
        solde("0", "0");

        DepotClientService.Operation recu = service.deposer(4L, new DepotClientService.DepotRequete(new BigDecimal("100000"),
                ModePaiementPdv.ESPECES, new BigDecimal("110000"), null, null, null), caissier());

        assertThat(recu.type()).isEqualTo(DepotClient.Type.DEPOT);
        assertThat(recu.statut()).isEqualTo(DepotClient.Statut.VALIDE);
        assertThat(recu.monnaieRendue()).isEqualByComparingTo("10000");
        assertThat(enregistres.get(0).getSession()).isSameAs(session);
    }

    @Test
    @DisplayName("Un achat ou un remboursement ne depasse pas le solde du depot")
    void soldeInsuffisant() {
        solde("20000", "0");

        assertThatThrownBy(() -> service.exigerSolde(4L, new BigDecimal("25000")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("20");
    }

    @Test
    @DisplayName("Demande par un caissier, le remboursement attend le gerant ; valide, les especes sortent de la caisse")
    void remboursementValideParLeGerant() {
        solde("50000", "0");
        session.setStatut(SessionCaisse.Statut.OUVERTE);

        DepotClientService.Operation demande = service.demanderRemboursement(4L, new DepotClientService.RemboursementRequete(
                new BigDecimal("30000"), DepotClient.Source.CAISSE, 3L, "Chantier annule"), caissier());

        assertThat(demande.statut()).isEqualTo(DepotClient.Statut.EN_ATTENTE);
        verify(caisses, never()).retraitRemise(anyLong(), any(), anyString(), any());

        DepotClient d = enregistres.get(0);
        d.setId(11L);
        when(repository.findById(11L)).thenReturn(Optional.of(d));
        when(caisses.especesDuTiroir(3L)).thenReturn(new BigDecimal("80000"));
        service.validerRemboursement(11L, gerant());

        assertThat(d.getStatut()).isEqualTo(DepotClient.Statut.VALIDE);
        verify(caisses).retraitRemise(eq(3L), eq(new BigDecimal("30000.00")), anyString(), any());
    }

    private static UtilisateurPrincipal caissier() {
        return principal(5L, Role.CAISSIER);
    }

    private static UtilisateurPrincipal gerant() {
        return principal(9L, Role.GERANT);
    }

    private static UtilisateurPrincipal principal(Long id, Role role) {
        Utilisateur u = new Utilisateur();
        u.setId(id);
        u.setEmail("u" + id + "@test.tg");
        u.setNom("Compte " + id);
        u.setMotDePasse("x");
        u.setRoles(EnumSet.of(role));
        u.setActif(true);
        u.setEntrepriseId(1L);
        return new UtilisateurPrincipal(u, "Test", true);
    }
}
