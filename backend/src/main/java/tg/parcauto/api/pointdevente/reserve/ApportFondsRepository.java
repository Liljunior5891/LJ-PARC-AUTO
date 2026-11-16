package tg.parcauto.api.pointdevente.reserve;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ApportFondsRepository extends JpaRepository<ApportFonds, Long> {

    List<ApportFonds> findAllByOrderByDateApportAsc();

    List<ApportFonds> findByAssocieIdOrderByDateApportDesc(Long associeId);

    List<ApportFonds> findByOrigineOrderByDateApportAsc(ApportFonds.Origine origine);

    @Query("select coalesce(sum(a.montant), 0) from ApportFonds a")
    BigDecimal total();

    @Query("select coalesce(sum(a.montant), 0) from ApportFonds a where a.origine = :origine")
    BigDecimal totalPar(@Param("origine") ApportFonds.Origine origine);

    @Query("select coalesce(sum(a.montant), 0) from ApportFonds a where a.associeId = :associe")
    BigDecimal totalAssocie(@Param("associe") Long associeId);
}
