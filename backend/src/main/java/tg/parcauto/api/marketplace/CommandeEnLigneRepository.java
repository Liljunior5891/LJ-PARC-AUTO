package tg.parcauto.api.marketplace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface CommandeEnLigneRepository extends JpaRepository<CommandeEnLigne, Long> {

    Optional<CommandeEnLigne> findByJeton(String jeton);

    Optional<CommandeEnLigne> findFirstByVenteId(Long venteId);

    Page<CommandeEnLigne> findByStatutInOrderByDateCommandeDesc(Collection<CommandeEnLigne.Statut> statuts,
                                                                Pageable pageable);

    Page<CommandeEnLigne> findAllByOrderByDateCommandeDesc(Pageable pageable);

    long countByStatut(CommandeEnLigne.Statut statut);

    /** Commandes confiees a la caisse (sans commande de salle) qui attendent encore la caisse. */
    @org.springframework.data.jpa.repository.Query("""
            select c from CommandeEnLigne c
            where c.enCaisse = true and c.pdvCommandeId is null
              and c.statut in (tg.parcauto.api.marketplace.CommandeEnLigne.Statut.NOUVELLE,
                               tg.parcauto.api.marketplace.CommandeEnLigne.Statut.CONFIRMEE,
                               tg.parcauto.api.marketplace.CommandeEnLigne.Statut.PRETE,
                               tg.parcauto.api.marketplace.CommandeEnLigne.Statut.EN_LIVRAISON)
            order by c.dateCommande
            """)
    java.util.List<CommandeEnLigne> aTraiterEnCaisse();

    java.util.List<CommandeEnLigne> findByPdvCommandeIdIn(Collection<Long> ids);
}
