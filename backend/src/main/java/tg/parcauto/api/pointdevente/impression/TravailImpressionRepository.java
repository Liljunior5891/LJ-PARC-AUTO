package tg.parcauto.api.pointdevente.impression;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;

public interface TravailImpressionRepository extends JpaRepository<TravailImpression, Long> {

    /** Bons a imprimer par le navigateur, pour une caisse (ou pour toutes : caisseId nul). */
    @Query("""
            select t from TravailImpression t join fetch t.imprimante i
            where t.statut in :statuts
              and i.actif = true and i.mode = tg.parcauto.api.pointdevente.impression.Imprimante.Mode.NAVIGATEUR
              and (i.caisse is null or :caisseId is null or i.caisse.id = :caisseId)
            order by t.id
            """)
    List<TravailImpression> aImprimer(@Param("statuts") Collection<TravailImpression.Statut> statuts,
                                      @Param("caisseId") Long caisseId);

    @Query("select t from TravailImpression t join fetch t.imprimante where t.statut in :statuts")
    List<TravailImpression> enAttente(@Param("statuts") Collection<TravailImpression.Statut> statuts);

    /** Verrouille les bons pris par une caisse : deux caisses ne les impriment pas en double. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TravailImpression t join fetch t.imprimante where t.id in :ids order by t.id")
    List<TravailImpression> verrouiller(@Param("ids") Collection<Long> ids);

    @Query("select t from TravailImpression t join fetch t.imprimante where t.commandeId = :id order by t.id desc")
    List<TravailImpression> deLaCommande(@Param("id") Long commandeId);

    @Query("select t from TravailImpression t join fetch t.imprimante where t.venteId = :id order by t.id desc")
    List<TravailImpression> deLaVente(@Param("id") Long venteId);
}
