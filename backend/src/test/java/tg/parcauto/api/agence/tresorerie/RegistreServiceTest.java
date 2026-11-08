package tg.parcauto.api.agence.tresorerie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tg.parcauto.api.agence.ReglageAgence;
import tg.parcauto.api.agence.ReglageAgenceRepository;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regles du registre (cahier des charges, cas de recette 1, 2 et 4) : un depot
 * ecrit une ligne par compte, un retrait superieur au float est refuse avec le
 * montant manquant, une annulation passe par l'ecriture inverse, une seule fois.
 */
class RegistreServiceTest {

    private RegistreService service;
    private EcritureRepository ecritures;
    private LigneRegistreRepository lignes;
    private CompteAgence cash;
    private CompteAgence floatMixx;
    private final List<LigneRegistre> ecrites = new ArrayList<>();
    private tg.parcauto.api.agence.cloture.VerrouJournee verrou;

    @BeforeEach
    void preparer() {
        CompteAgenceRepository comptes = mock(CompteAgenceRepository.class);
        ecritures = mock(EcritureRepository.class);
        lignes = mock(LigneRegistreRepository.class);
        ReglageAgenceRepository reglages = mock(ReglageAgenceRepository.class);
        NumerotationService numerotation = mock(NumerotationService.class);
        cash = compte(1L, "Caisse generale", 0);
        floatMixx = compte(2L, "Float Mixx by Yas", 150_000);
        // Le verrouillage relit les comptes en base ; ici, les deux comptes en memoire en tiennent lieu.
        verrou = mock(tg.parcauto.api.agence.cloture.VerrouJournee.class);
        service = new RegistreService(comptes, ecritures, lignes, reglages, numerotation, verrou) {
            @Override
            public List<CompteAgence> verrouiller(Collection<Long> ids) {
                return List.of(cash, floatMixx).stream().filter(c -> ids.contains(c.getId())).toList();
            }
        };
        when(reglages.findFirstByOrderByIdAsc()).thenReturn(Optional.of(new ReglageAgence()));
        when(numerotation.prochainNumero(anyString())).thenReturn("ECR-2026-0001");
        when(ecritures.save(any())).thenAnswer(i -> {
            Ecriture e = i.getArgument(0);
            e.setId(50L);
            return e;
        });
        when(lignes.saveAll(any())).thenAnswer(i -> {
            Iterable<LigneRegistre> l = i.getArgument(0);
            l.forEach(ecrites::add);
            return l;
        });
    }

    private static CompteAgence compte(Long id, String libelle, long solde) {
        CompteAgence c = new CompteAgence();
        c.setId(id);
        c.setAgenceId(9L);
        c.setLibelle(libelle);
        c.setSolde(solde);
        return c;
    }

    private RegistreService.Entete entete(NatureEcriture nature) {
        return new RegistreService.Entete(9L, nature, "Operation", null, null, null);
    }

    @Test
    void depotDeCentMilleSurMixxEcritUneLigneParCompte() {
        service.enregistrer(entete(NatureEcriture.TRANSACTION), List.of(
                RegistreService.Mouvement.de(1L, 100_000), RegistreService.Mouvement.de(2L, -100_000)));

        assertThat(cash.getSolde()).isEqualTo(100_000);
        assertThat(floatMixx.getSolde()).isEqualTo(50_000);
        assertThat(ecrites).hasSize(2);
        assertThat(ecrites).extracting(LigneRegistre::getSoldeApres).containsExactly(100_000L, 50_000L);
    }

    @Test
    void retraitSuperieurAuFloatEstRefuseAvecLeMontantManquant() {
        floatMixx.setSolde(25_000);
        assertThatThrownBy(() -> service.enregistrer(entete(NatureEcriture.TRANSACTION), List.of(
                RegistreService.Mouvement.de(2L, -50_000), RegistreService.Mouvement.de(1L, 50_000))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Float Mixx by Yas insuffisant : il manque 25 000 F");
        assertThat(floatMixx.getSolde()).isEqualTo(25_000);
        assertThat(cash.getSolde()).isZero();
        assertThat(ecrites).isEmpty();
    }

    @Test
    void leDecouvertAutoriseEstTolere() {
        floatMixx.setSolde(10_000);
        floatMixx.setDecouvertAutorise(5_000);
        service.enregistrer(entete(NatureEcriture.TRANSACTION), List.of(RegistreService.Mouvement.de(2L, -15_000)));
        assertThat(floatMixx.getSolde()).isEqualTo(-5_000);
    }

    @Test
    void annulationEcritLInverseEtNeSeFaitQuUneFois() {
        Ecriture origine = new Ecriture();
        origine.setId(40L);
        origine.setAgenceId(9L);
        origine.setNumero("ECR-2026-0007");
        origine.setLibelle("Depot Mixx");
        origine.setNature(NatureEcriture.TRANSACTION);
        when(ecritures.findById(40L)).thenReturn(Optional.of(origine));
        LigneRegistre l1 = new LigneRegistre();
        l1.setCompteId(1L);
        l1.setMontant(100_000);
        LigneRegistre l2 = new LigneRegistre();
        l2.setCompteId(2L);
        l2.setMontant(-100_000);
        when(lignes.findByEcritureIdOrderByIdAsc(40L)).thenReturn(List.of(l1, l2));
        cash.setSolde(100_000);
        floatMixx.setSolde(50_000);

        Ecriture annulation = service.contrePasser(40L, "Erreur de saisie");

        assertThat(annulation.getNature()).isEqualTo(NatureEcriture.ANNULATION);
        assertThat(annulation.getEcritureAnnuleeId()).isEqualTo(40L);
        assertThat(cash.getSolde()).isZero();
        assertThat(floatMixx.getSolde()).isEqualTo(150_000);

        when(ecritures.existsByEcritureAnnuleeId(anyLong())).thenReturn(true);
        assertThatThrownBy(() -> service.contrePasser(40L, "Encore"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("deja ete annulee");
    }

    @Test
    void annulationSansMotifEstRefusee() {
        assertThatThrownBy(() -> service.contrePasser(40L, " "))
                .isInstanceOf(BusinessException.class).hasMessageContaining("motif");
    }

    @Test
    void compteDUneAutreAgenceEstRefuse() {
        floatMixx.setAgenceId(10L);
        assertThatThrownBy(() -> service.enregistrer(entete(NatureEcriture.TRANSACTION),
                List.of(RegistreService.Mouvement.de(2L, 1_000))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("n'appartient pas");
    }

    @Test
    void journeeDeCaisseSuitLHeureDeBascule() {
        Instant uneHeureDuMatin = Instant.parse("2026-11-07T01:00:00Z");
        assertThat(JourneeCaisse.de(uneHeureDuMatin, 0)).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(JourneeCaisse.de(uneHeureDuMatin, 6)).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(JourneeCaisse.de(Instant.parse("2026-11-07T06:00:00Z"), 6)).isEqualTo(LocalDate.of(2026, 11, 7));
    }

    @Test
    void journeeClotureeRefuseToutEcriture() {
        org.mockito.Mockito.doThrow(new BusinessException("La journee du 07/11/2026 est cloturee : aucune ecriture impossible"))
                .when(verrou).exigerOuverte(org.mockito.ArgumentMatchers.eq(9L), any(), anyString());
        assertThatThrownBy(() -> service.enregistrer(entete(NatureEcriture.TRANSACTION),
                List.of(RegistreService.Mouvement.de(1L, 1_000))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("cloturee");
        assertThat(cash.getSolde()).isZero();
        assertThat(ecrites).isEmpty();
    }
}
