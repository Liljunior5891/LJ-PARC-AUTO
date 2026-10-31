package tg.parcauto.api.pointdevente.approvisionnement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommandeFournisseurRepository extends JpaRepository<CommandeFournisseur, Long> {

    Page<CommandeFournisseur> findAllByOrderByDateCommandeDescIdDesc(Pageable pageable);

    Page<CommandeFournisseur> findByStatutOrderByDateCommandeDescIdDesc(CommandeFournisseur.Statut statut,
                                                                         Pageable pageable);

    @Query("select distinct c from CommandeFournisseur c left join fetch c.lignes l left join fetch l.article "
            + "where c.id = :id")
    Optional<CommandeFournisseur> detail(@Param("id") Long id);

    long countByStatut(CommandeFournisseur.Statut statut);

    @Query("select distinct c from CommandeFournisseur c left join fetch c.lignes "
            + "where c.dateCommande >= :debut and c.dateCommande <= :fin order by c.dateCommande desc, c.id desc")
    List<CommandeFournisseur> periode(@Param("debut") java.time.LocalDate debut, @Param("fin") java.time.LocalDate fin);

    /** Commandes en cours dont la livraison prevue est depassee. */
    @Query("select count(c) from CommandeFournisseur c "
            + "where c.statut = tg.parcauto.api.pointdevente.approvisionnement.CommandeFournisseur.Statut.EN_COURS "
            + "and c.livraisonPrevue < :aujourdhui")
    long enRetard(@Param("aujourdhui") java.time.LocalDate aujourdhui);

    /** Valeur des marchandises receptionnees sur la periode, au cout de la commande. */
    @Query("select coalesce(sum(l.quantiteRecue * coalesce(l.coutUnitaire, 0)), 0) "
            + "from LigneCommandeFournisseur l where l.commande.recueLe >= :debut and l.commande.recueLe < :fin")
    java.math.BigDecimal achatsRecus(@Param("debut") java.time.Instant debut, @Param("fin") java.time.Instant fin);

    /** Fournisseurs deja utilises, proposes a la saisie d'une nouvelle commande. */
    @Query("select distinct c.fournisseur from CommandeFournisseur c order by c.fournisseur")
    List<String> fournisseurs();
}
