package tg.parcauto.api.pointdevente.restauration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tg.parcauto.api.pointdevente.catalogue.PostePreparation;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface LigneCommandeRepository extends JpaRepository<LigneCommande, Long> {

    List<LigneCommande> findByVenteId(Long venteId);

    /** Lignes parties en preparation sur la periode, pour les categories d'un ecran. */
    @Query("select l from LigneCommande l join fetch l.commande c left join fetch c.table join fetch l.article a "
            + "where l.envoyeeLe >= :debut and l.envoyeeLe < :fin and a.categorie.id in :categories "
            + "order by l.envoyeeLe, c.id")
    List<LigneCommande> envoyeesParCategories(@Param("categories") Collection<Long> categories,
                                              @Param("debut") java.time.Instant debut, @Param("fin") java.time.Instant fin);

    /** Lignes d'un ecran de preparation : a faire, en cours, et pretes depuis peu. */
    @Query("select l from LigneCommande l join fetch l.commande c left join fetch c.table "
            + "where l.poste = :poste "
            + "and c.statut <> tg.parcauto.api.pointdevente.restauration.Commande.Statut.ANNULEE "
            + "and (l.statut in :enCours "
            + "or (l.statut = tg.parcauto.api.pointdevente.restauration.LigneCommande.Statut.PRETE "
            + "and l.preteLe >= :pretesDepuis)) "
            + "order by l.envoyeeLe, l.id")
    List<LigneCommande> aPreparer(@Param("poste") PostePreparation poste,
                                  @Param("enCours") Collection<LigneCommande.Statut> enCours,
                                  @Param("pretesDepuis") Instant pretesDepuis);

    /** Lignes d'un ecran de preparation : les articles de ses categories, hors articles servis tels quels. */
    @Query("select l from LigneCommande l join fetch l.commande c left join fetch c.table "
            + "where l.article.categorie.id in :categories "
            + "and l.poste <> tg.parcauto.api.pointdevente.catalogue.PostePreparation.AUCUN "
            + "and c.statut <> tg.parcauto.api.pointdevente.restauration.Commande.Statut.ANNULEE "
            + "and (l.statut in :enCours "
            + "or (l.statut = tg.parcauto.api.pointdevente.restauration.LigneCommande.Statut.PRETE "
            + "and l.preteLe >= :pretesDepuis)) "
            + "order by l.envoyeeLe, l.id")
    List<LigneCommande> aPreparerParCategories(@Param("categories") Collection<Long> categories,
                                  @Param("enCours") Collection<LigneCommande.Statut> enCours,
                                  @Param("pretesDepuis") Instant pretesDepuis);
}
