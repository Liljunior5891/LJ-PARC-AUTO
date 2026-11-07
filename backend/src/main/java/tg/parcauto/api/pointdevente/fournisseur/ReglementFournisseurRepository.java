package tg.parcauto.api.pointdevente.fournisseur;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface ReglementFournisseurRepository extends JpaRepository<ReglementFournisseur, Long> {

    @Query("""
            select r from ReglementFournisseur r join fetch r.commande left join fetch r.session
            where r.fournisseur.id = :id order by r.dateReglement desc, r.id desc
            """)
    List<ReglementFournisseur> duFournisseur(@Param("id") Long fournisseurId);

    @Query("""
            select r from ReglementFournisseur r join fetch r.commande join fetch r.fournisseur
            left join fetch r.session left join fetch r.mouvementCaisse
            where r.numero = :numero order by r.id
            """)
    List<ReglementFournisseur> parNumero(@Param("numero") String numero);

    @Query("""
            select r from ReglementFournisseur r join fetch r.fournisseur
            where r.commande.id = :id order by r.dateReglement desc, r.id desc
            """)
    List<ReglementFournisseur> deLaCommande(@Param("id") Long commandeId);

    /** [commande id, montant regle] des reglements valides des commandes donnees. */
    @Query("""
            select r.commande.id, sum(r.montant) from ReglementFournisseur r
            where r.commande.id in :ids
              and r.statut = tg.parcauto.api.pointdevente.fournisseur.ReglementFournisseur.Statut.VALIDE
            group by r.commande.id
            """)
    List<Object[]> reglesParCommande(@Param("ids") Collection<Long> commandeIds);

    @Query("""
            select r from ReglementFournisseur r join fetch r.commande join fetch r.fournisseur
            where r.dateReglement >= :debut and r.dateReglement <= :fin
            order by r.dateReglement desc, r.id desc
            """)
    List<ReglementFournisseur> periode(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    @Query("""
            select coalesce(sum(r.montant), 0) from ReglementFournisseur r
            where r.dateReglement >= :debut and r.dateReglement <= :fin
              and r.statut = tg.parcauto.api.pointdevente.fournisseur.ReglementFournisseur.Statut.VALIDE
            """)
    BigDecimal totalPeriode(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    /** Total des paiements valides sortis du coffre d'un gerant. */
    @Query("""
            select coalesce(sum(r.montant), 0) from ReglementFournisseur r
            where r.coffreId = :gerant and r.mode = tg.parcauto.api.pointdevente.depense.ModeSortie.COFFRE
              and r.statut = tg.parcauto.api.pointdevente.fournisseur.ReglementFournisseur.Statut.VALIDE
            """)
    BigDecimal sortiDuCoffre(@Param("gerant") Long gerantId);

    /** Paiements valides d'un moyen de paiement jusqu'a une date (journaux de banque et du coffre). */
    @Query("""
            select r from ReglementFournisseur r join fetch r.fournisseur join fetch r.commande
            where r.mode = :mode and r.dateReglement <= :fin
              and r.statut = tg.parcauto.api.pointdevente.fournisseur.ReglementFournisseur.Statut.VALIDE
            order by r.dateReglement, r.id
            """)
    List<ReglementFournisseur> payesPar(@Param("mode") tg.parcauto.api.pointdevente.depense.ModeSortie mode,
                                        @Param("fin") LocalDate fin);
}
