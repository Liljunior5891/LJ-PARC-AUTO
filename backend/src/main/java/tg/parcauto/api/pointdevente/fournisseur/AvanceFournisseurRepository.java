package tg.parcauto.api.pointdevente.fournisseur;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tg.parcauto.api.pointdevente.depense.ModeSortie;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AvanceFournisseurRepository extends JpaRepository<AvanceFournisseur, Long> {

    Optional<AvanceFournisseur> findByNumero(String numero);

    List<AvanceFournisseur> findByFournisseurIdOrderByDateOperationDescIdDesc(Long fournisseurId);

    /** [fournisseurId, avances valides - remboursements valides] par fiche. */
    @Query("""
            select a.fournisseur.id, coalesce(sum(case when a.type = tg.parcauto.api.pointdevente.fournisseur.AvanceFournisseur.Type.AVANCE
                                                       then a.montant else -a.montant end), 0)
            from AvanceFournisseur a
            where a.statut = tg.parcauto.api.pointdevente.fournisseur.AvanceFournisseur.Statut.VALIDE
              and (:fournisseurId is null or a.fournisseur.id = :fournisseurId)
            group by a.fournisseur.id
            """)
    List<Object[]> netParFiche(@Param("fournisseurId") Long fournisseurId);

    /** Especes sorties du coffre d'un gerant par des avances valides. */
    @Query("""
            select coalesce(sum(a.montant), 0) from AvanceFournisseur a
            where a.coffreId = :gerant and a.mode = tg.parcauto.api.pointdevente.depense.ModeSortie.COFFRE
              and a.type = tg.parcauto.api.pointdevente.fournisseur.AvanceFournisseur.Type.AVANCE
              and a.statut = tg.parcauto.api.pointdevente.fournisseur.AvanceFournisseur.Statut.VALIDE
            """)
    BigDecimal sortiDuCoffre(@Param("gerant") Long gerantId);

    /** Operations valides d'un moyen de paiement, jusqu'a une date (compte de l'entreprise, coffres). */
    @Query("""
            select a from AvanceFournisseur a join fetch a.fournisseur
            where a.mode = :mode and a.statut = tg.parcauto.api.pointdevente.fournisseur.AvanceFournisseur.Statut.VALIDE
              and a.dateOperation <= :jusquau
            order by a.dateOperation, a.id
            """)
    List<AvanceFournisseur> validesPar(@Param("mode") ModeSortie mode, @Param("jusquau") LocalDate jusquau);
}
