package tg.parcauto.api.agence.carte;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LotCarteRepository extends JpaRepository<LotCarte, Long> {

    List<LotCarte> findByAgenceIdInOrderByDateEntreeDescIdDesc(Collection<Long> agenceIds);

    /** Lots encore garnis, du plus ancien au plus recent, verrouilles pour une vente. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from LotCarte l
            where l.agenceId = :agence and l.produitId = :produit and l.quantiteRestante > 0
            order by l.dateEntree, l.id
            """)
    List<LotCarte> disponiblesVerrouilles(@Param("agence") Long agenceId, @Param("produit") Long produitId);
}
