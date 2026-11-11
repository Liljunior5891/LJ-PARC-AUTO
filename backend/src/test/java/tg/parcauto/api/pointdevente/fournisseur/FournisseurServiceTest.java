package tg.parcauto.api.pointdevente.fournisseur;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.pointdevente.approvisionnement.CommandeFournisseur;
import tg.parcauto.api.pointdevente.approvisionnement.CommandeFournisseurRepository;
import tg.parcauto.api.pointdevente.caisse.CaisseService;
import tg.parcauto.api.pointdevente.caisse.MouvementCaisse;
import tg.parcauto.api.pointdevente.caisse.SessionCaisse;
import tg.parcauto.api.pointdevente.depense.ModeSortie;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.Utilisateur;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FournisseurServiceTest {

    private FournisseurService service;
    private ReglementFournisseurRepository reglements;
    private CaisseService caisse;
    private final List<ReglementFournisseur> enregistres = new ArrayList<>();
    private CommandeFournisseur ancienne;
    private CommandeFournisseur recente;

    @BeforeEach
    void preparer() {
        FournisseurRepository fiches = mock(FournisseurRepository.class);
        reglements = mock(ReglementFournisseurRepository.class);
        CommandeFournisseurRepository commandes = mock(CommandeFournisseurRepository.class);
        caisse = mock(CaisseService.class);
        NumerotationService numerotation = mock(NumerotationService.class);
        when(numerotation.prochainNumero("RF")).thenReturn("RF-2026-0001");

        Fournisseur brasserie = new Fournisseur();
        brasserie.setId(7L);
        brasserie.setNom("Brasserie BB");
        when(fiches.findById(7L)).thenReturn(Optional.of(brasserie));

        ancienne = commande(1L, "BC-1", brasserie, "60000", LocalDate.now().minusDays(5));
        recente = commande(2L, "BC-2", brasserie, "40000", LocalDate.now().plusDays(20));
        when(commandes.dettes(7L)).thenReturn(List.of(ancienne, recente));
        when(reglements.reglesParCommande(any())).thenAnswer(i -> enregistres.stream()
                .filter(r -> r.getStatut() == ReglementFournisseur.Statut.VALIDE)
                .collect(java.util.stream.Collectors.groupingBy(r -> r.getCommande().getId(),
                        java.util.stream.Collectors.reducing(BigDecimal.ZERO, ReglementFournisseur::getMontant, BigDecimal::add)))
                .entrySet().stream().map(e -> new Object[]{e.getKey(), e.getValue()}).toList());
        when(reglements.save(any())).thenAnswer(i -> {
            enregistres.add(i.getArgument(0));
            return i.getArgument(0);
        });
        service = new FournisseurService(fiches, reglements, commandes, caisse, numerotation,
                mock(tg.parcauto.api.pointdevente.tresorerie.TresorerieService.class),
                mock(tg.parcauto.api.pointdevente.reglage.ReglagePdvService.class));
    }

    @Test
    @DisplayName("Un paiement sans commande choisie solde d'abord l'echeance la plus ancienne")
    void imputationDesPlusAnciennes() {
        FournisseurService.Recu recu = service.regler(7L, new FournisseurService.ReglementRequete(
                new BigDecimal("75000"), ModeSortie.BANQUE, null, null, null, "VIR-12", null), gerant());

        assertThat(recu.imputations()).extracting(FournisseurService.Imputation::commandeNumero)
                .containsExactly("BC-1", "BC-2");
        assertThat(recu.imputations().get(0).montant()).isEqualByComparingTo("60000");
        assertThat(recu.imputations().get(1).montant()).isEqualByComparingTo("15000");
        assertThat(recu.resteDu()).isEqualByComparingTo("25000");
        verify(caisse, never()).sortie(any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("On ne paie pas plus que ce qui est du")
    void montantPlafonne() {
        assertThatThrownBy(() -> service.regler(7L, new FournisseurService.ReglementRequete(
                new BigDecimal("100001"), ModeSortie.BANQUE, null, null, null, null, null), gerant()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("depasse");
    }

    @Test
    @DisplayName("Paye depuis la caisse, le reglement cree une sortie d'especes dans la session choisie")
    void sortieDeCaisse() {
        MouvementCaisse sortie = new MouvementCaisse();
        SessionCaisse session = new SessionCaisse();
        session.setNumero("CAI-2026-0003");
        sortie.setSession(session);
        when(caisse.sortie(eq(3L), any(), anyString(), any())).thenReturn(sortie);

        FournisseurService.Recu recu = service.regler(7L, new FournisseurService.ReglementRequete(
                new BigDecimal("40000"), ModeSortie.CAISSE, 3L, 2L, null, null, null), gerant());

        ArgumentCaptor<BigDecimal> montant = ArgumentCaptor.forClass(BigDecimal.class);
        verify(caisse).sortie(eq(3L), montant.capture(), anyString(), any());
        assertThat(montant.getValue()).isEqualByComparingTo("40000");
        assertThat(recu.sessionNumero()).isEqualTo("CAI-2026-0003");
        assertThat(enregistres).singleElement().satisfies(r -> {
            assertThat(r.getCommande()).isSameAs(recente);
            assertThat(r.getMouvementCaisse()).isSameAs(sortie);
        });
    }

    @Test
    @DisplayName("Le mode caisse exige une session ouverte")
    void caisseSansSession() {
        assertThatThrownBy(() -> service.regler(7L, new FournisseurService.ReglementRequete(
                BigDecimal.TEN, ModeSortie.CAISSE, null, null, null, null, null), gerant()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("caisse");
    }

    @Test
    @DisplayName("L'annulation rend la dette et retire la sortie de caisse")
    void annulation() {
        MouvementCaisse sortie = new MouvementCaisse();
        sortie.setSession(new SessionCaisse());
        when(caisse.sortie(eq(3L), any(), anyString(), any())).thenReturn(sortie);
        service.regler(7L, new FournisseurService.ReglementRequete(
                new BigDecimal("60000"), ModeSortie.CAISSE, 3L, 1L, null, null, null), gerant());
        when(reglements.parNumero("RF-2026-0001")).thenReturn(List.copyOf(enregistres));

        FournisseurService.Recu annule = service.annuler("RF-2026-0001", "Erreur de saisie", gerant());

        verify(caisse).retirerSortie(eq(sortie), anyString());
        assertThat(annule.statut()).isEqualTo(ReglementFournisseur.Statut.ANNULE);
        assertThat(annule.resteDu()).isEqualByComparingTo("100000");
    }

    private static CommandeFournisseur commande(Long id, String numero, Fournisseur f, String recu, LocalDate echeance) {
        CommandeFournisseur c = new CommandeFournisseur();
        c.setId(id);
        c.setNumero(numero);
        c.setFiche(f);
        c.setFournisseur(f.getNom());
        c.setStatut(CommandeFournisseur.Statut.RECUE);
        c.setRecueLe(Instant.now());
        c.setMontantRecu(new BigDecimal(recu));
        c.setEcheance(echeance);
        return c;
    }

    private static UtilisateurPrincipal gerant() {
        Utilisateur u = new Utilisateur();
        u.setId(9L);
        u.setEmail("g@test.tg");
        u.setNom("Gerant");
        u.setMotDePasse("x");
        u.setRoles(EnumSet.of(Role.GERANT));
        u.setActif(true);
        u.setEntrepriseId(1L);
        return new UtilisateurPrincipal(u, "Test", true);
    }
}
