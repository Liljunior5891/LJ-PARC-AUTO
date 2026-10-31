package tg.parcauto.api.pointdevente.restauration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommandeRepository extends JpaRepository<Commande, Long> {

    @Query("select distinct c from Commande c left join fetch c.table left join fetch c.lignes "
            + "where c.statut = tg.parcauto.api.pointdevente.restauration.Commande.Statut.OUVERTE "
            + "order by c.ouverteLe")
    List<Commande> ouvertes();

    @Query("select c from Commande c left join fetch c.table left join fetch c.lignes where c.id = :id")
    Optional<Commande> detail(@Param("id") Long id);

    boolean existsByTableIdAndStatut(Long tableId, Commande.Statut statut);

    long countByStatut(Commande.Statut statut);

    /**
     * Commandes encore ouvertes qui empechent de fermer une session : les
     * siennes, et celles rattachees a aucune session quand c'est la derniere ouverte.
     */
    @Query("select c from Commande c left join fetch c.table "
            + "where c.statut = tg.parcauto.api.pointdevente.restauration.Commande.Statut.OUVERTE "
            + "and (c.sessionId = :sessionId or (:sansSession = true and c.sessionId is null)) order by c.ouverteLe")
    List<Commande> enCoursPourSession(@Param("sessionId") Long sessionId, @Param("sansSession") boolean sansSession);

    @Query("select distinct c from Commande c left join fetch c.table left join fetch c.lignes "
            + "where c.ouverteLe >= :debut and c.ouverteLe < :fin order by c.ouverteLe desc")
    List<Commande> periode(@Param("debut") java.time.Instant debut, @Param("fin") java.time.Instant fin);

    Page<Commande> findByStatutOrderByOuverteLeDesc(Commande.Statut statut, Pageable pageable);
}
