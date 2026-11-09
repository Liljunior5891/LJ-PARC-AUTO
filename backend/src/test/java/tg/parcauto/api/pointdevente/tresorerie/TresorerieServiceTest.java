package tg.parcauto.api.pointdevente.tresorerie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.pointdevente.caisse.Caisse;
import tg.parcauto.api.pointdevente.caisse.CaisseService;
import tg.parcauto.api.pointdevente.caisse.MouvementCaisse;
import tg.parcauto.api.pointdevente.caisse.SessionCaisse;
import tg.parcauto.api.pointdevente.reglage.ReglagePdvService;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.Utilisateur;
import tg.parcauto.api.security.UtilisateurPrincipal;
import tg.parcauto.api.security.UtilisateurRepository;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TresorerieServiceTest {

    private TresorerieService service;
    private RemiseCaisseRepository remises;
    private DegagementCoffreRepository degagements;
    private CaisseService caisse;
    private SessionCaisse session;

    @BeforeEach
    void preparer() {
        remises = mock(RemiseCaisseRepository.class);
        degagements = mock(DegagementCoffreRepository.class);
        caisse = mock(CaisseService.class);
        ReglagePdvService reglages = mock(ReglagePdvService.class);
        when(reglages.lire()).thenReturn(new ReglagePdvService.Reglages(false, true, new BigDecimal("500"), false, 5));
        NumerotationService numerotation = mock(NumerotationService.class);
        when(numerotation.prochainNumero(anyString())).thenAnswer(i -> i.getArgument(0) + "-2026-0001");
        when(remises.save(any())).thenAnswer(i -> i.getArgument(0));
        when(degagements.save(any())).thenAnswer(i -> i.getArgument(0));

        Caisse comptoir = new Caisse();
        comptoir.setNom("Comptoir");
        comptoir.setUtilisateurIds(Set.of(5L));
        session = new SessionCaisse();
        session.setId(3L);
        session.setNumero("CAI-2026-0003");
        session.setCaisse(comptoir);
        session.setFondInitial(new BigDecimal("20000"));
        when(caisse.sessionOuverte(3L)).thenReturn(session);
        // Tiroir : 20 000 de fond + 80 000 de ventes en especes.
        when(caisse.especesDuTiroir(3L)).thenReturn(new BigDecimal("100000"));
        when(caisse.retraitRemise(eq(3L), any(), anyString(), any())).thenReturn(new MouvementCaisse());

        var depenses = mock(tg.parcauto.api.pointdevente.depense.DepensePdvRepository.class);
        when(depenses.sortiDuCoffre(any())).thenReturn(BigDecimal.ZERO);
        var paiements = mock(tg.parcauto.api.pointdevente.fournisseur.ReglementFournisseurRepository.class);
        when(paiements.sortiDuCoffre(any())).thenReturn(BigDecimal.ZERO);
        service = new TresorerieService(remises, degagements, caisse, reglages, numerotation,
                mock(UtilisateurRepository.class), depenses, paiements,
                mock(tg.parcauto.api.pointdevente.vente.VenteRepository.class),
                mock(tg.parcauto.api.pointdevente.materiaux.ReglementClientRepository.class),
                mock(tg.parcauto.api.parc.document.StockageFichierService.class));
    }

    @Test
    @DisplayName("Le caissier remet ce que contient le tiroir, fond de caisse deduit")
    void remiseAttendueSansLeFond() {
        TresorerieService.RemiseDto r = service.envoyerRemise(3L, envoi(8, null), utilisateur(5L, Role.CAISSIER));

        assertThat(r.montantAttendu()).isEqualByComparingTo("80000");
        assertThat(r.montantDeclare()).isEqualByComparingTo("80000");
        assertThat(r.statut()).isEqualTo(RemiseCaisse.Statut.EN_ATTENTE);
        verify(caisse, never()).retraitRemise(any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("Un ecart au-dela de la tolerance doit etre justifie")
    void ecartAJustifier() {
        assertThatThrownBy(() -> service.envoyerRemise(3L, envoi(7, null), utilisateur(5L, Role.CAISSIER)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("justifiez");
        TresorerieService.RemiseDto r = service.envoyerRemise(3L, envoi(7, "Billet de 10 000 refuse"),
                utilisateur(5L, Role.CAISSIER));
        assertThat(r.ecartDeclare()).isEqualByComparingTo("-10000");
    }

    @Test
    @DisplayName("Un caissier ne remet que la recette d'une caisse qui lui est affectee")
    void caisseNonAffectee() {
        assertThatThrownBy(() -> service.envoyerRemise(3L, envoi(8, null), utilisateur(6L, Role.CAISSIER)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pas affectee");
    }

    @Test
    @DisplayName("Validee par le gerant, la somme recomptee quitte le tiroir pour son coffre")
    void validationParLeGerant() {
        RemiseCaisse r = enAttente();
        TresorerieService.RemiseDto valide = service.validerRemise(r.getId(),
                new TresorerieService.Validation(List.of(new TresorerieService.Ligne(new BigDecimal("10000"), 8)), null),
                utilisateur(2L, Role.GERANT));

        verify(caisse).retraitRemise(eq(3L), eq(new BigDecimal("80000")), anyString(), any());
        assertThat(r.getCoffreId()).isEqualTo(2L);
        assertThat(valide.destination()).startsWith("Coffre de");
    }

    @Test
    @DisplayName("Validee par l'administrateur, la remise va directement au compte de l'entreprise")
    void validationParLAdministrateur() {
        RemiseCaisse r = enAttente();
        service.validerRemise(r.getId(),
                new TresorerieService.Validation(List.of(new TresorerieService.Ligne(new BigDecimal("10000"), 8)), null),
                utilisateur(1L, Role.ADMIN));
        assertThat(r.getCoffreId()).isNull();
    }

    @Test
    @DisplayName("Le degagement part du solde du coffre et attend l'administrateur")
    void degagement() {
        when(remises.recuParCoffre(2L)).thenReturn(new BigDecimal("80000"));
        when(degagements.sortiParCoffre(2L)).thenReturn(BigDecimal.ZERO);
        when(degagements.findFirstByGerantIdAndStatut(2L, DegagementCoffre.Statut.EN_ATTENTE)).thenReturn(Optional.empty());

        TresorerieService.DegagementDto d = service.envoyerDegagement(depot(8), utilisateur(2L, Role.GERANT));

        assertThat(d.montantAttendu()).isEqualByComparingTo("80000");
        assertThat(d.statut()).isEqualTo(DegagementCoffre.Statut.EN_ATTENTE);
        assertThat(d.referenceDepot()).isEqualTo("BV-2026-118");
        assertThatThrownBy(() -> service.envoyerDegagement(depot(8), utilisateur(1L, Role.ADMIN)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Un depot en banque ne se valide qu'avec le bordereau joint")
    void depotSansPiece() {
        DegagementCoffre d = new DegagementCoffre();
        d.setId(70L);
        d.setNumero("DEG-2026-0001");
        d.setGerantId(2L);
        d.setGerantNom("Gerant");
        d.setMontantAttendu(new BigDecimal("80000"));
        d.setMontantDeclare(new BigDecimal("80000"));
        d.setEnvoyeLe(java.time.Instant.now());
        when(degagements.findById(70L)).thenReturn(Optional.of(d));
        TresorerieService.ValidationDepot validation = new TresorerieService.ValidationDepot(new BigDecimal("80000"), null);

        assertThatThrownBy(() -> service.validerDegagement(70L, validation, utilisateur(1L, Role.ADMIN)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("bordereau");

        d.setPieceChemin("pdv-degagements/70/bordereau.pdf");
        TresorerieService.DegagementDto valide = service.validerDegagement(70L, validation, utilisateur(1L, Role.ADMIN));
        assertThat(valide.statut()).isEqualTo(DegagementCoffre.Statut.VALIDE);
        assertThat(valide.montantValide()).isEqualByComparingTo("80000");
    }

    @Test
    @DisplayName("Seul un gerant paie depuis son coffre, dans la limite de son solde")
    void paiementDepuisLeCoffre() {
        when(remises.recuParCoffre(2L)).thenReturn(new BigDecimal("50000"));
        when(degagements.sortiParCoffre(2L)).thenReturn(BigDecimal.ZERO);

        assertThat(service.exigerCoffre(utilisateur(2L, Role.GERANT), new BigDecimal("30000"))).isEqualTo(2L);
        assertThatThrownBy(() -> service.exigerCoffre(utilisateur(2L, Role.GERANT), new BigDecimal("60000")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ne contient que");
        assertThatThrownBy(() -> service.exigerCoffre(utilisateur(1L, Role.ADMIN), BigDecimal.TEN))
                .isInstanceOf(BusinessException.class);
    }

    private static TresorerieService.EnvoiDepot depot(int billetsDe10000) {
        return new TresorerieService.EnvoiDepot(List.of(new TresorerieService.Ligne(new BigDecimal("10000"), billetsDe10000)),
                null, "Ecobank", "BV-2026-118", java.time.LocalDate.now());
    }

    private RemiseCaisse enAttente() {
        RemiseCaisse r = new RemiseCaisse();
        r.setId(40L);
        r.setNumero("REM-2026-0001");
        r.setSession(session);
        r.setCaissierId(5L);
        r.setCaissierNom("Ama");
        r.setMontantAttendu(new BigDecimal("80000"));
        r.setMontantDeclare(new BigDecimal("80000"));
        r.setEnvoyeeLe(java.time.Instant.now());
        when(remises.findById(40L)).thenReturn(Optional.of(r));
        return r;
    }

    private static TresorerieService.Envoi envoi(int billetsDe10000, String justification) {
        return new TresorerieService.Envoi(List.of(new TresorerieService.Ligne(new BigDecimal("10000"), billetsDe10000)),
                justification);
    }

    private static UtilisateurPrincipal utilisateur(Long id, Role role) {
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
