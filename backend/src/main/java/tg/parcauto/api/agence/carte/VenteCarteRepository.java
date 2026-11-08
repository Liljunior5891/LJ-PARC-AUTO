package tg.parcauto.api.agence.carte;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface VenteCarteRepository extends JpaRepository<VenteCarte, Long> {

    Page<VenteCarte> findByAgenceIdInOrderByDateVenteDescIdDesc(Collection<Long> agenceIds, Pageable pageable);

    /** [nombre de ventes, cartes vendues, chiffre d'affaires, cout, marge] des ventes validees. */
    @Query("""
            select count(v), coalesce(sum(v.quantite), 0), coalesce(sum(v.montant), 0), coalesce(sum(v.coutTotal), 0),
                   coalesce(sum(v.marge), 0)
            from VenteCarte v
            where v.agenceId in :agences and v.statut = tg.parcauto.api.agence.carte.VenteCarte.Statut.VALIDEE
              and v.journee >= :du and v.journee <= :au
            """)
    List<Object[]> totaux(@Param("agences") Collection<Long> agences, @Param("du") LocalDate du, @Param("au") LocalDate au);
}
