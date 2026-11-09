package tg.parcauto.api.finance.depense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface ReglementFournisseurParcRepository extends JpaRepository<ReglementFournisseurParc, Long> {

    List<ReglementFournisseurParc> findByDepenseIdOrderByDateReglementAscIdAsc(Long depenseId);

    /** Total regle (reglements valides) d'une depense. */
    @Query("""
            select coalesce(sum(r.montant), 0) from ReglementFournisseurParc r
            where r.depense.id = :depense
              and r.statut = tg.parcauto.api.finance.depense.ReglementFournisseurParc.Statut.VALIDE
            """)
    BigDecimal regle(@Param("depense") Long depenseId);

    /** [depense id, total regle] pour plusieurs depenses. */
    @Query("""
            select r.depense.id, coalesce(sum(r.montant), 0) from ReglementFournisseurParc r
            where r.depense.id in :depenses
              and r.statut = tg.parcauto.api.finance.depense.ReglementFournisseurParc.Statut.VALIDE
            group by r.depense.id
            """)
    List<Object[]> regles(@Param("depenses") Collection<Long> depenseIds);

    /** Reglements d'un fournisseur, les plus recents d'abord. */
    @Query("""
            select r from ReglementFournisseurParc r join fetch r.depense d
            where d.fournisseurParc.id = :fournisseur
            order by r.dateReglement desc, r.id desc
            """)
    List<ReglementFournisseurParc> duFournisseur(@Param("fournisseur") Long fournisseurId);
}
