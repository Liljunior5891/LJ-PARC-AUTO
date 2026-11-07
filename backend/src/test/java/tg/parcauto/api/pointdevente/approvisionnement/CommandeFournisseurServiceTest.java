package tg.parcauto.api.pointdevente.approvisionnement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tg.parcauto.api.common.BusinessException;
import tg.parcauto.api.common.NumerotationService;
import tg.parcauto.api.pointdevente.catalogue.Article;
import tg.parcauto.api.pointdevente.catalogue.ArticleRepository;
import tg.parcauto.api.pointdevente.stock.MouvementStockRepository;
import tg.parcauto.api.pointdevente.stock.StockService;
import tg.parcauto.api.security.Role;
import tg.parcauto.api.security.Utilisateur;
import tg.parcauto.api.security.UtilisateurPrincipal;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandeFournisseurServiceTest {

    private CommandeFournisseurService service;
    private CommandeFournisseurRepository repository;
    private Article biere;
    private Article coca;

    @BeforeEach
    void preparer() {
        repository = mock(CommandeFournisseurRepository.class);
        ArticleRepository articles = mock(ArticleRepository.class);
        MouvementStockRepository mouvements = mock(MouvementStockRepository.class);
        when(mouvements.save(any())).thenAnswer(i -> i.getArgument(0));
        biere = article(1L, "Biere", "10", "500");
        coca = article(2L, "Coca", "4", "300");
        when(articles.findById(1L)).thenReturn(Optional.of(biere));
        when(articles.findById(2L)).thenReturn(Optional.of(coca));
        when(repository.saveAndFlush(any())).thenAnswer(i -> {
            CommandeFournisseur c = i.getArgument(0);
            c.setId(50L);
            long n = 100;
            for (LigneCommandeFournisseur l : c.getLignes()) {
                l.setId(n++);
            }
            when(repository.detail(50L)).thenReturn(Optional.of(c));
            return c;
        });
        tg.parcauto.api.pointdevente.fournisseur.FournisseurRepository fiches =
                mock(tg.parcauto.api.pointdevente.fournisseur.FournisseurRepository.class);
        when(fiches.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        tg.parcauto.api.pointdevente.fournisseur.FournisseurService fournisseurs =
                new tg.parcauto.api.pointdevente.fournisseur.FournisseurService(fiches,
                        mock(tg.parcauto.api.pointdevente.fournisseur.ReglementFournisseurRepository.class), repository,
                        mock(tg.parcauto.api.pointdevente.caisse.CaisseService.class), mock(NumerotationService.class),
                        mock(tg.parcauto.api.pointdevente.tresorerie.TresorerieService.class));
        service = new CommandeFournisseurService(repository, articles,
                new StockService(articles, mouvements, org.mockito.Mockito.mock(tg.parcauto.api.pointdevente.lieu.LieuStockService.class)), org.mockito.Mockito.mock(tg.parcauto.api.pointdevente.lieu.LieuStockService.class), mock(NumerotationService.class),
                fournisseurs);
    }

    @Test
    @DisplayName("La commande ne touche pas au stock ; la reception y fait entrer les quantites recues")
    void receptionAlimenteLeStock() {
        service.creer(new CommandeFournisseurService.Requete(null, "Brasserie BB Lome", null, null, null, List.of(
                new CommandeFournisseurService.LigneRequete(1L, new BigDecimal("24"), new BigDecimal("520")),
                new CommandeFournisseurService.LigneRequete(2L, new BigDecimal("12"), null))));
        assertThat(biere.getStockActuel()).isEqualByComparingTo("10");

        // Seulement 20 bieres livrees sur 24 ; les coca arrivent en totalite au cout habituel.
        CommandeFournisseurService.Detail recue = service.receptionner(50L, new CommandeFournisseurService.Reception(
                List.of(new CommandeFournisseurService.LigneReception(100L, new BigDecimal("20"), null))), gerant());

        assertThat(recue.resume().statut()).isEqualTo(CommandeFournisseur.Statut.RECUE);
        assertThat(biere.getStockActuel()).isEqualByComparingTo("30");
        assertThat(coca.getStockActuel()).isEqualByComparingTo("16");
        assertThat(biere.getPrixAchat()).isEqualByComparingTo("520");
        assertThat(recue.lignes().get(0).quantiteRecue()).isEqualByComparingTo("20");
        // La valeur recue devient la dette envers le fournisseur : 20 x 520 + 12 x 300.
        assertThat(recue.resume().montantRecu()).isEqualByComparingTo("14000");
        assertThat(recue.paiement().reste()).isEqualByComparingTo("14000");
        assertThat(recue.resume().echeance()).isNotNull();
    }

    @Test
    @DisplayName("Une commande receptionnee n'est plus modifiable ni receptionnable")
    void commandeFigee() {
        service.creer(new CommandeFournisseurService.Requete(null, "Brasserie", null, null, null,
                List.of(new CommandeFournisseurService.LigneRequete(1L, BigDecimal.ONE, null))));
        service.receptionner(50L, null, gerant());

        assertThatThrownBy(() -> service.receptionner(50L, null, gerant()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("deja receptionnee");
    }

    @Test
    @DisplayName("Un meme produit ne figure qu'une fois sur la commande")
    void doublonRefuse() {
        assertThatThrownBy(() -> service.creer(new CommandeFournisseurService.Requete(null, "X", null, null, null, List.of(
                new CommandeFournisseurService.LigneRequete(1L, BigDecimal.ONE, null),
                new CommandeFournisseurService.LigneRequete(1L, BigDecimal.TEN, null)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("deux fois");
    }

    private static Article article(Long id, String nom, String stock, String cout) {
        Article a = new Article();
        a.setId(id);
        a.setNom(nom);
        a.setSuiviStock(true);
        a.setStockActuel(new BigDecimal(stock));
        a.setPrixAchat(new BigDecimal(cout));
        return a;
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
